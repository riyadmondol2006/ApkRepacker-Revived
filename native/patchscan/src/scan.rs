//! Which files can match: the files are read on a thread pool and searched with the relaxed
//! regexes and literal needles. A file counts as a candidate when it might match; only a file that
//! was read, is valid UTF-8 and has no match of any query is ruled out.
//!
//! The text search (find in files) is stricter about the text: Java guesses each file's encoding
//! there, and may read even valid UTF-8 as GBK or UTF-16, so only ASCII files are ruled out.

use memchr::memmem::Finder;
use rayon::prelude::*;
use regex::bytes::{Regex, RegexBuilder, RegexSet, RegexSetBuilder};

use crate::relax::{relax, relax_case_insensitive, Unsupported};

/// Room for big relaxed patterns (`\w`, `.` and `\s` are Unicode classes); a pattern that still
/// doesn't fit is refused and scanned in Java.
const SIZE_LIMIT: usize = 64 << 20;

pub struct Queries {
    /// Java regexes, relaxed.
    regexes: Option<RegexSet>,
    /// Sets of lines that must all occur in the file (the trimmed lines of a non-regex MATCH).
    line_sets: Vec<Vec<Finder<'static>>>,
}

impl Queries {
    pub fn new(java_regexes: &[String], line_sets: &[Vec<String>]) -> Result<Queries, Unsupported> {
        let relaxed = java_regexes.iter().map(|r| relax(r)).collect::<Result<Vec<_>, _>>()?;
        let regexes = if relaxed.is_empty() {
            None
        } else {
            Some(
                RegexSetBuilder::new(&relaxed)
                    .size_limit(SIZE_LIMIT)
                    .dfa_size_limit(SIZE_LIMIT)
                    .build()
                    .map_err(|_| Unsupported("relaxed regex does not compile"))?,
            )
        };
        let line_sets = line_sets
            .iter()
            .map(|lines| lines.iter().map(|l| Finder::new(l.as_bytes()).into_owned()).collect())
            .collect();
        Ok(Queries { regexes, line_sets })
    }

    /// Whether [text] (valid UTF-8) can contain a match of any query.
    pub fn can_match(&self, text: &[u8]) -> bool {
        if self.regexes.as_ref().is_some_and(|set| set.is_match(text)) {
            return true;
        }
        self.line_sets.iter().any(|set| set.iter().all(|f| f.find(text).is_some()))
    }
}

/// For each of [files] (paths relative to [root], joined like the Java side does: `root/file`),
/// whether it can match.
pub fn candidates(root: &str, files: &[String], queries: &Queries, threads: usize) -> Vec<bool> {
    let check = |file: &String| -> bool {
        let Ok(bytes) = std::fs::read(format!("{root}/{file}")) else {
            // Unreadable: Java reads it and reports the error.
            return true;
        };
        if std::str::from_utf8(&bytes).is_err() {
            // Java decodes broken UTF-8 to U+FFFD, which the relaxed regex doesn't model.
            return true;
        }
        queries.can_match(&bytes)
    };
    map_files(files, threads, check)
}

/// One Java regex of the text search (the whole pattern, with the `\b`s of whole-word mode),
/// relaxed.
pub struct TextQuery {
    regex: Regex,
}

impl TextQuery {
    pub fn new(java_regex: &str, case_insensitive: bool) -> Result<TextQuery, Unsupported> {
        let relaxed = if case_insensitive { relax_case_insensitive(java_regex)? } else { relax(java_regex)? };
        let regex = RegexBuilder::new(&relaxed)
            .case_insensitive(case_insensitive)
            .size_limit(SIZE_LIMIT)
            .dfa_size_limit(SIZE_LIMIT)
            .build()
            .map_err(|_| Unsupported("relaxed regex does not compile"))?;
        Ok(TextQuery { regex })
    }

    /// Whether [text] can contain a match: any non-ASCII text can (its encoding is guessed in
    /// Java, and case-insensitive matching of it isn't modelled), ASCII text when the relaxed
    /// regex finds something.
    pub fn can_match(&self, text: &[u8]) -> bool {
        !text.is_ascii() || self.regex.is_match(text)
    }
}

/// Bigger files aren't read here (one per thread could take a lot of memory): they are candidates.
const MAX_TEXT_FILE: u64 = 64 << 20;

/// For each of [files] (absolute paths), whether it can contain a match of [query]. Unreadable
/// and huge files count as candidates: Java decides what to do with them.
pub fn text_candidates(files: &[String], query: &TextQuery, threads: usize) -> Vec<bool> {
    map_files(files, threads, |file: &String| {
        if std::fs::metadata(file).map_or(true, |m| m.len() > MAX_TEXT_FILE) {
            return true;
        }
        match std::fs::read(file) {
            Ok(bytes) => !is_binary_format(&bytes) && query.can_match(&bytes),
            Err(_) => true,
        }
    })
}

/// Images, archives, dex, native libraries, fonts and compiled Android XML, by their first bytes:
/// the text search skips them (their bytes only ever gave garbage "matches"). Must stay the same
/// as ExtGrep.isBinaryFormat, which applies the rule when this library isn't used.
pub fn is_binary_format(b: &[u8]) -> bool {
    b.starts_with(b"\x89PNG\r\n\x1a\n")
        || b.starts_with(b"\xff\xd8\xff")
        || b.starts_with(b"GIF87a")
        || b.starts_with(b"GIF89a")
        || (b.starts_with(b"RIFF") && b.get(8..12) == Some(b"WEBP"))
        || b.starts_with(b"PK\x03\x04")
        || (b.starts_with(b"dex\n") && b.get(7) == Some(&0))
        || b.starts_with(b"\x7fELF")
        || b.starts_with(b"\x00\x01\x00\x00")
        || b.starts_with(b"OTTO")
        || b.starts_with(b"wOFF")
        || b.starts_with(b"wOF2")
        || b.starts_with(b"\x03\x00\x08\x00")
        || b.starts_with(b"\x02\x00\x0c\x00")
}

fn map_files(files: &[String], threads: usize, check: impl Fn(&String) -> bool + Sync) -> Vec<bool> {
    match rayon::ThreadPoolBuilder::new().num_threads(threads.max(1)).build() {
        Ok(pool) => pool.install(|| files.par_iter().map(&check).collect()),
        Err(_) => files.iter().map(check).collect(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn files() {
        let dir = std::env::temp_dir().join(format!("patchscan-test-{}", std::process::id()));
        std::fs::create_dir_all(dir.join("smali/a")).unwrap();
        std::fs::write(dir.join("smali/a/Hit.smali"), "    const-string v0, \"premium\"\n").unwrap();
        std::fs::write(dir.join("smali/a/Miss.smali"), "    const-string v0, \"free\"\n").unwrap();
        std::fs::write(dir.join("smali/a/Bad.smali"), b"\xff\xfe broken").unwrap();
        let root = dir.to_str().unwrap();
        let files: Vec<String> =
            ["smali/a/Hit.smali", "smali/a/Miss.smali", "smali/a/Bad.smali", "smali/a/Gone.smali"].map(String::from).to_vec();

        let q = Queries::new(&[r#"const-string ([pv]\d+), "premium""#.to_string()], &[]).unwrap();
        assert_eq!(candidates(root, &files, &q, 4), vec![true, false, true, true]);

        let lines = Queries::new(&[], &[vec!["const-string v0, \"free\"".to_string()]]).unwrap();
        assert_eq!(candidates(root, &files, &lines, 1), vec![false, true, true, true]);

        let both = Queries::new(&["premium".to_string()], &[vec!["free".to_string()]]).unwrap();
        assert_eq!(candidates(root, &files, &both, 2), vec![true, true, true, true]);

        assert!(Queries::new(&["(?i)premium".to_string()], &[]).is_err());
        std::fs::remove_dir_all(dir).unwrap();
    }

    #[test]
    fn text_files() {
        let dir = std::env::temp_dir().join(format!("patchscan-text-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let write = |name: &str, bytes: &[u8]| {
            let path = dir.join(name);
            std::fs::write(&path, bytes).unwrap();
            path.to_str().unwrap().to_string()
        };
        let files = vec![
            write("Hit.smali", b"    const-string v0, \"Premium\"\n"),
            write("Miss.smali", b"    const-string v0, \"free\"\n"),
            // Valid UTF-8, but Java may guess another encoding: always a candidate.
            write("values.xml", "<string name=\"a\">Привет</string>\n".as_bytes()),
            // An image: skipped by the text search whatever it contains.
            write("icon.png", b"\x89PNG\r\n\x1a\n\x00\x00"),
            dir.join("Gone.smali").to_str().unwrap().to_string(),
        ];

        let exact = TextQuery::new(r#"const-string v0, "Premium""#, false).unwrap();
        assert_eq!(text_candidates(&files, &exact, 2), vec![true, false, true, false, true]);
        let lower = TextQuery::new("premium", false).unwrap();
        assert_eq!(text_candidates(&files, &lower, 2), vec![false, false, true, false, true]);
        let ci = TextQuery::new("premium", true).unwrap();
        assert_eq!(text_candidates(&files, &ci, 2), vec![true, false, true, false, true]);
        let word = TextQuery::new(r"\bFREE\b", true).unwrap();
        assert_eq!(text_candidates(&files, &word, 1), vec![false, true, true, false, true]);
        let regex = TextQuery::new(r"const-string [pv]\d+, \x22f", false).unwrap();
        assert_eq!(text_candidates(&files, &regex, 4), vec![false, true, true, false, true]);

        assert!(TextQuery::new("straße", true).is_err());
        assert!(TextQuery::new("(?x)a b", true).is_err());
        assert!(TextQuery::new("[a", false).is_err());
        std::fs::remove_dir_all(dir).unwrap();
    }

    #[test]
    fn binary_formats_are_skipped() {
        for b in [&b"\x89PNG\r\n\x1a\nIHDR getDeviceId"[..], b"\xff\xd8\xff\xe0 getDeviceId", b"PK\x03\x04getDeviceId",
            b"dex\n035\0getDeviceId", b"\x7fELF getDeviceId", b"RIFF\0\0\0\0WEBPgetDeviceId", b"\x03\x00\x08\x00getDeviceId"] {
            assert!(is_binary_format(b), "{b:?}");
        }
        for b in [&b"getDeviceId"[..], b"<?xml version", b".class public La;", b"GIF", b"PK", b"RIFF text", b"dex\nnot"] {
            assert!(!is_binary_format(b), "{b:?}");
        }
    }
}

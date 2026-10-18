//! Which files can match: the files are read on a thread pool and searched with the relaxed
//! regexes and literal needles. A file counts as a candidate when it might match; only a file that
//! was read, is valid UTF-8 and has no match of any query is ruled out.

use memchr::memmem::Finder;
use rayon::prelude::*;
use regex::bytes::{RegexSet, RegexSetBuilder};

use crate::relax::{relax, Unsupported};

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
    match rayon::ThreadPoolBuilder::new().num_threads(threads.max(1)).build() {
        Ok(pool) => pool.install(|| files.par_iter().map(check).collect()),
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
}

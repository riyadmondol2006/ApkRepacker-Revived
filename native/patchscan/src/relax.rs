//! Java regex -> "relaxed" Rust regex.
//!
//! The relaxed regex matches at least every string the Java one matches (Android's
//! java.util.regex, which runs on ICU, as well as desktop Java), so a file it finds nothing in
//! cannot contain a Java match either. It may match more: anchors, word boundaries and
//! lookarounds become empty, backreferences match anything, `.` also matches line breaks, and
//! character classes Java and ICU might read differently match any character. Whatever can't be
//! widened safely (comments mode, nested classes and set operations, unknown escapes) is refused
//! with [`Unsupported`], and the caller scans the files in Java as before.
//!
//! Case-insensitive patterns ([`relax_case_insensitive`]) are only widened for ASCII text: ICU may
//! fold case fully (`ß` matches `ss`), which Rust's simple folding doesn't, so a pattern with a
//! non-ASCII character is refused and the caller must treat every non-ASCII file as a match. For
//! an ASCII pattern on ASCII text both fold the same way.

use std::fmt::Write;

#[derive(Debug, PartialEq, Eq)]
pub struct Unsupported(pub &'static str);

type Result<T> = std::result::Result<T, Unsupported>;

/// Any one character, line breaks included, or a CR LF pair: ICU's `.` in DOTALL mode consumes
/// `\r\n` as one character.
const ANY: &str = "(?s:\r\n|.)";
/// Matches everywhere: what an assertion (`^`, `$`, `\b`, a lookaround) is widened to.
const EMPTY: &str = "(?:)";
/// Anything at all: a backreference (it repeats some earlier text).
const ANY_TEXT: &str = "(?s:.*)";

// Shorthand classes. Positive ones are the union of Java's ASCII meaning and ICU's Unicode one;
// negated ones are the complement of the smallest of them, so both stay supersets.
const DIGIT: &str = r"\d";
const NOT_DIGIT: &str = "[^0-9]";
const SPACE_ITEMS: &str = r"\s\x0B\x1C-\x1F";
const NOT_SPACE: &str = r"[^\t\n\x0C\r ]";
const WORD_ITEMS: &str = r"\w\x{200C}\x{200D}";
const NOT_WORD: &str = "[^0-9A-Za-z_]";

/// Counted repetitions above this are widened to `*` / `+`, which keeps the compiled regex small.
const MAX_COUNT: u32 = 100;
/// Deeper group nesting is refused (the parser recurses per group).
const MAX_DEPTH: usize = 200;

/// The relaxed Rust form of the Java regex [pattern] (flags: none, or DOTALL, which changes nothing
/// here since `.` already matches line breaks).
pub fn relax(pattern: &str) -> Result<String> {
    relax_with(pattern, false)
}

/// The relaxed form of the Java regex [pattern] compiled with CASE_INSENSITIVE, to be compiled
/// case-insensitively too. It is a superset of the Java regex on ASCII text only: the pattern
/// must be ASCII (else it is refused), and non-ASCII text must count as a match without it.
/// Negated sets (`[^a]`, `\W`, ...), whose case closure ICU may build differently, match any
/// character.
pub fn relax_case_insensitive(pattern: &str) -> Result<String> {
    relax_with(pattern, true)
}

fn relax_with(pattern: &str, case_insensitive: bool) -> Result<String> {
    let mut p = Parser { s: pattern.chars().collect(), i: 0, depth: 0, ci: case_insensitive };
    let out = p.alternation()?;
    if p.i != p.s.len() {
        return Err(Unsupported("unbalanced ')'"));
    }
    Ok(out)
}

struct Parser {
    s: Vec<char>,
    i: usize,
    depth: usize,
    /// Case-insensitive mode: ASCII characters only, negated sets match anything.
    ci: bool,
}

impl Parser {
    fn peek(&self) -> Option<char> {
        self.s.get(self.i).copied()
    }

    fn peek_at(&self, k: usize) -> Option<char> {
        self.s.get(self.i + k).copied()
    }

    fn next(&mut self) -> Option<char> {
        let c = self.peek();
        if c.is_some() {
            self.i += 1;
        }
        c
    }

    fn eat(&mut self, c: char) -> bool {
        if self.peek() == Some(c) {
            self.i += 1;
            true
        } else {
            false
        }
    }

    fn alternation(&mut self) -> Result<String> {
        let mut out = self.sequence()?;
        while self.eat('|') {
            out.push('|');
            out.push_str(&self.sequence()?);
        }
        Ok(out)
    }

    fn sequence(&mut self) -> Result<String> {
        let mut out = String::new();
        while let Some(c) = self.peek() {
            if c == '|' || c == ')' {
                break;
            }
            let mut atom = if c == '\\' && self.peek_at(1) == Some('Q') {
                // \Q...\E: literal text. A quantifier after it applies to its last character.
                self.i += 2;
                let mut quoted = Vec::new();
                while let Some(q) = self.peek() {
                    if q == '\\' && self.peek_at(1) == Some('E') {
                        self.i += 2;
                        break;
                    }
                    quoted.push(q);
                    self.i += 1;
                }
                match quoted.pop() {
                    Some(last) => {
                        for q in quoted {
                            self.check_char(q)?;
                            push_literal(&mut out, q);
                        }
                        self.literal(last)?
                    }
                    None => {
                        if matches!(self.peek(), Some('*' | '+' | '?' | '{')) {
                            return Err(Unsupported("quantifier after an empty \\Q\\E"));
                        }
                        continue;
                    }
                }
            } else {
                self.atom()?
            };
            while let Some(q) = self.quantifier()? {
                atom = format!("(?:{atom}){q}");
            }
            out.push_str(&atom);
        }
        Ok(out)
    }

    fn atom(&mut self) -> Result<String> {
        let c = self.next().ok_or(Unsupported("unexpected end"))?;
        match c {
            '(' => self.group(),
            '[' => self.class(),
            '.' => Ok(ANY.into()),
            '^' | '$' => Ok(EMPTY.into()),
            '\\' => self.escape(),
            '*' | '+' | '?' | '{' => Err(Unsupported("dangling quantifier")),
            c => self.literal(c),
        }
    }

    /// `*`, `+`, `?` or `{n}`, `{n,}`, `{n,m}`, with an optional lazy `?` or possessive `+` after
    /// it. Lazy and possessive forms match a subset of what the greedy one can, so the greedy
    /// form is used.
    fn quantifier(&mut self) -> Result<Option<String>> {
        let q = match self.peek() {
            Some('*') => {
                self.i += 1;
                "*".to_string()
            }
            Some('+') => {
                self.i += 1;
                "+".to_string()
            }
            Some('?') => {
                self.i += 1;
                "?".to_string()
            }
            Some('{') => {
                self.i += 1;
                let min = self.number()?;
                let max = if self.eat(',') {
                    if self.peek() == Some('}') {
                        None
                    } else {
                        Some(self.number()?)
                    }
                } else {
                    Some(min)
                };
                if !self.eat('}') {
                    return Err(Unsupported("bad counted repetition"));
                }
                if max.is_some_and(|m| m < min) {
                    return Err(Unsupported("bad counted repetition"));
                }
                if min > MAX_COUNT || max.is_some_and(|m| m > MAX_COUNT) {
                    if min == 0 { "*" } else { "+" }.to_string()
                } else {
                    match max {
                        Some(m) => format!("{{{min},{m}}}"),
                        None => format!("{{{min},}}"),
                    }
                }
            }
            _ => return Ok(None),
        };
        if !self.eat('?') {
            self.eat('+');
        }
        Ok(Some(q))
    }

    fn number(&mut self) -> Result<u32> {
        let start = self.i;
        let mut n: u32 = 0;
        while let Some(d) = self.peek().and_then(|c| c.to_digit(10)) {
            n = n.checked_mul(10).and_then(|n| n.checked_add(d)).ok_or(Unsupported("number too big"))?;
            self.i += 1;
        }
        if self.i == start {
            return Err(Unsupported("expected a number"));
        }
        Ok(n)
    }

    /// After `(`.
    fn group(&mut self) -> Result<String> {
        let mut keep = true;
        if self.eat('?') {
            match self.next() {
                Some(':') | Some('>') => {}
                Some('=') | Some('!') => keep = false,
                Some('<') => match self.peek() {
                    Some('=') | Some('!') => {
                        self.i += 1;
                        keep = false;
                    }
                    Some(c) if c.is_ascii_alphabetic() => self.group_name()?,
                    _ => return Err(Unsupported("bad group")),
                },
                Some(c) if is_flag(c) || c == '-' => {
                    self.i -= 1;
                    if !self.flags()? {
                        // (?flags) alone: sets flags for the rest, matches nothing itself.
                        return Ok(String::new());
                    }
                }
                _ => return Err(Unsupported("bad group")),
            }
        }
        self.depth += 1;
        if self.depth > MAX_DEPTH {
            return Err(Unsupported("groups nested too deep"));
        }
        let inner = self.alternation()?;
        self.depth -= 1;
        if !self.eat(')') {
            return Err(Unsupported("unclosed group"));
        }
        Ok(if keep { format!("(?:{inner})") } else { EMPTY.into() })
    }

    /// `name>` of `(?<name>`.
    fn group_name(&mut self) -> Result<()> {
        while self.peek().is_some_and(|c| c.is_ascii_alphanumeric()) {
            self.i += 1;
        }
        if self.eat('>') { Ok(()) } else { Err(Unsupported("bad group name")) }
    }

    /// Inline flags `idmsuxU-...` up to `)` (returns false) or `:` (true). Turning on comments
    /// mode is refused, and case-insensitive mode too unless the whole pattern already is (then
    /// `-i` is ignored as well: matching without case still finds every case-sensitive match).
    /// The other flags don't change the relaxed regex.
    fn flags(&mut self) -> Result<bool> {
        let mut on = true;
        loop {
            match self.next() {
                Some(')') => return Ok(false),
                Some(':') => return Ok(true),
                Some('-') => on = false,
                Some('i') if on && !self.ci => return Err(Unsupported("case-insensitive flag")),
                Some('x') if on => return Err(Unsupported("comments flag")),
                Some(c) if is_flag(c) => {}
                _ => return Err(Unsupported("bad flags")),
            }
        }
    }

    /// After `\` outside a class.
    fn escape(&mut self) -> Result<String> {
        let c = self.next().ok_or(Unsupported("trailing backslash"))?;
        Ok(match c {
            'd' => DIGIT.into(),
            's' => format!("[{SPACE_ITEMS}]"),
            'w' => format!("[{WORD_ITEMS}]"),
            'D' | 'S' | 'W' if self.ci => ANY.into(),
            'D' => NOT_DIGIT.into(),
            'S' => NOT_SPACE.into(),
            'W' => NOT_WORD.into(),
            'h' | 'H' | 'v' | 'V' => ANY.into(),
            'R' => "(?s:.{1,2})".into(),
            'X' => "(?s:.+)".into(),
            'p' | 'P' => {
                self.property()?;
                ANY.into()
            }
            'b' | 'B' => {
                if self.peek() == Some('{') {
                    self.skip_braces()?;
                }
                EMPTY.into()
            }
            'A' | 'z' | 'Z' | 'G' => EMPTY.into(),
            'k' => {
                if !self.eat('<') {
                    return Err(Unsupported("bad \\k"));
                }
                self.group_name()?;
                ANY_TEXT.into()
            }
            '1'..='9' => {
                while self.peek().is_some_and(|c| c.is_ascii_digit()) {
                    self.i += 1;
                }
                ANY_TEXT.into()
            }
            c => {
                let ch = self.char_escape(c)?;
                self.literal(ch)?
            }
        })
    }

    /// Case-insensitive mode can't widen non-ASCII characters (see [`relax_case_insensitive`]).
    fn check_char(&self, c: char) -> Result<()> {
        if self.ci && !c.is_ascii() {
            return Err(Unsupported("non-ASCII character in a case-insensitive pattern"));
        }
        Ok(())
    }

    fn literal(&self, c: char) -> Result<String> {
        self.check_char(c)?;
        Ok(literal(c))
    }

    /// The character a character escape stands for (`\t`, `\x41`, `A`, `\0101`, `\cA`, `\.`).
    fn char_escape(&mut self, c: char) -> Result<char> {
        let ch = match c {
            't' => '\t',
            'n' => '\n',
            'r' => '\r',
            'f' => '\x0C',
            'a' => '\x07',
            'e' => '\x1B',
            '0' => {
                // \0n, \0nn, \0mnn (m <= 3)
                let mut v = 0u32;
                let mut digits = 0;
                while digits < 3 {
                    let Some(d) = self.peek().and_then(|c| c.to_digit(8)) else { break };
                    if digits == 2 && v > 0o37 {
                        break;
                    }
                    v = v * 8 + d;
                    digits += 1;
                    self.i += 1;
                }
                if digits == 0 {
                    return Err(Unsupported("bad octal escape"));
                }
                return char::from_u32(v).ok_or(Unsupported("bad octal escape"));
            }
            'x' => {
                let v = if self.eat('{') {
                    let v = self.hex(1, 8)?;
                    if !self.eat('}') {
                        return Err(Unsupported("bad \\x{...}"));
                    }
                    v
                } else {
                    self.hex(2, 2)?
                };
                return char::from_u32(v).ok_or(Unsupported("bad \\x escape"));
            }
            'u' => {
                let v = self.hex(4, 4)?;
                if (0xD800..0xDC00).contains(&v) && self.peek() == Some('\\') && self.peek_at(1) == Some('u') {
                    self.i += 2;
                    let low = self.hex(4, 4)?;
                    if !(0xDC00..0xE000).contains(&low) {
                        return Err(Unsupported("lone surrogate"));
                    }
                    return char::from_u32(0x10000 + ((v - 0xD800) << 10) + (low - 0xDC00)).ok_or(Unsupported("bad surrogates"));
                }
                return char::from_u32(v).ok_or(Unsupported("lone surrogate"));
            }
            'c' => {
                // Java reads `\cX` as X ^ 0x40, ICU as X & 0x1F; they agree only for X in @..=_.
                let x = self.next().ok_or(Unsupported("bad \\c"))?;
                if !('@'..='_').contains(&x) {
                    return Err(Unsupported("\\c with a character Java and ICU read differently"));
                }
                return char::from_u32(x as u32 ^ 64).ok_or(Unsupported("bad \\c"));
            }
            c if c.is_ascii_alphanumeric() => return Err(Unsupported("unknown escape")),
            c => c,
        };
        Ok(ch)
    }

    /// Exactly [min]..=[max] hex digits (as many as there are, up to [max]).
    fn hex(&mut self, min: usize, max: usize) -> Result<u32> {
        let mut v = 0u32;
        let mut n = 0;
        while n < max {
            let Some(d) = self.peek().and_then(|c| c.to_digit(16)) else { break };
            v = v.checked_mul(16).and_then(|v| v.checked_add(d)).ok_or(Unsupported("hex escape too big"))?;
            n += 1;
            self.i += 1;
        }
        if n < min {
            return Err(Unsupported("bad hex escape"));
        }
        Ok(v)
    }

    /// `{Name}` or a one-letter name after `\p` / `\P`.
    fn property(&mut self) -> Result<()> {
        if self.peek() == Some('{') {
            self.skip_braces()
        } else if self.next().is_some_and(|c| c.is_ascii_alphabetic()) {
            Ok(())
        } else {
            Err(Unsupported("bad property"))
        }
    }

    fn skip_braces(&mut self) -> Result<()> {
        while let Some(c) = self.next() {
            if c == '}' {
                return Ok(());
            }
        }
        Err(Unsupported("unclosed {"))
    }

    /// After `[`. Flat classes (characters, ranges, shorthands) are kept as they are; ones with
    /// properties or `\h`/`\v` match any character, and so do negated ones in case-insensitive
    /// mode; nested classes and set operations, which Java and ICU parse differently, are refused.
    fn class(&mut self) -> Result<String> {
        let negated = self.eat('^');
        let mut items = String::new();
        // ICU reads a set that starts with `[:` (or `[^:`) as a POSIX-style property such as
        // `[:alpha:]` or `[:^L:]`, where Java sees the plain characters.
        let mut any = self.peek() == Some(':');
        let mut shorthand = false;
        let mut negated_shorthand = false;
        let mut first = true;
        loop {
            let c = self.next().ok_or(Unsupported("unclosed class"))?;
            match c {
                ']' if !first => break,
                ']' => return Err(Unsupported("']' first in a class")),
                '[' => return Err(Unsupported("nested class")),
                '&' if self.peek() == Some('&') => return Err(Unsupported("class intersection")),
                '-' if self.peek() == Some('-') => return Err(Unsupported("class difference")),
                '\\' => {
                    let e = self.next().ok_or(Unsupported("unclosed class"))?;
                    match e {
                        'd' => {
                            shorthand = true;
                            items.push_str(DIGIT);
                        }
                        's' => {
                            shorthand = true;
                            items.push_str(SPACE_ITEMS);
                        }
                        'w' => {
                            shorthand = true;
                            items.push_str(WORD_ITEMS);
                        }
                        'D' => {
                            negated_shorthand = true;
                            items.push_str(NOT_DIGIT);
                        }
                        'S' => {
                            negated_shorthand = true;
                            items.push_str(NOT_SPACE);
                        }
                        'W' => {
                            negated_shorthand = true;
                            items.push_str(NOT_WORD);
                        }
                        'p' | 'P' => {
                            any = true;
                            self.property()?;
                        }
                        'h' | 'H' | 'v' | 'V' => any = true,
                        e if e.is_ascii_alphanumeric() && !matches!(e, 't' | 'n' | 'r' | 'f' | 'a' | 'e' | '0' | 'x' | 'u' | 'c') => {
                            return Err(Unsupported("escape not supported in a class"));
                        }
                        e => {
                            let ch = self.char_escape(e)?;
                            self.class_char(ch, &mut items)?;
                        }
                    }
                }
                c => self.class_char(c, &mut items)?,
            }
            first = false;
        }
        shorthand |= negated_shorthand;
        if any || (negated && shorthand) || (self.ci && (negated || negated_shorthand)) {
            return Ok(ANY.into());
        }
        Ok(format!("[{}{items}]", if negated { "^" } else { "" }))
    }

    /// One class member starting with [start]: a range `start-end` or the character itself.
    fn class_char(&mut self, start: char, items: &mut String) -> Result<()> {
        if self.peek() == Some('-') && !matches!(self.peek_at(1), Some(']') | None) {
            self.i += 1;
            let end = match self.next().ok_or(Unsupported("unclosed class"))? {
                '\\' => {
                    let e = self.next().ok_or(Unsupported("unclosed class"))?;
                    if e.is_ascii_alphanumeric() && !matches!(e, 't' | 'n' | 'r' | 'f' | 'a' | 'e' | '0' | 'x' | 'u' | 'c') {
                        return Err(Unsupported("bad range end"));
                    }
                    self.char_escape(e)?
                }
                '[' => return Err(Unsupported("bad range end")),
                c => c,
            };
            if end < start {
                return Err(Unsupported("bad range"));
            }
            self.check_char(start)?;
            self.check_char(end)?;
            let _ = write!(items, "\\x{{{:X}}}-\\x{{{:X}}}", start as u32, end as u32);
        } else {
            self.check_char(start)?;
            let _ = write!(items, "\\x{{{:X}}}", start as u32);
        }
        Ok(())
    }
}

fn is_flag(c: char) -> bool {
    matches!(c, 'i' | 'd' | 'm' | 's' | 'u' | 'x' | 'U')
}

fn literal(c: char) -> String {
    let mut s = String::new();
    push_literal(&mut s, c);
    s
}

fn push_literal(out: &mut String, c: char) {
    if c.is_ascii_alphanumeric() || c == ' ' || c == '_' {
        out.push(c);
    } else {
        let _ = write!(out, "\\x{{{:X}}}", c as u32);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use regex::bytes::Regex;

    fn re(java: &str) -> Regex {
        let relaxed = relax(java).unwrap_or_else(|e| panic!("{java}: {e:?}"));
        Regex::new(&relaxed).unwrap_or_else(|e| panic!("{java} -> {relaxed}: {e}"))
    }

    fn finds(java: &str, text: &str) -> bool {
        re(java).is_match(text.as_bytes())
    }

    #[test]
    fn real_patch_patterns() {
        let smali = "    const-string v0, \"android_id\"\n\n    invoke-static {v1, v0}, Landroid/provider/Settings$Secure;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;\n";
        assert!(finds(
            r#"    const-string ([pv]\d+), \"android_id\"\n\n    invoke-static \{([pv]\d+), ([pv]\d+)\}, Landroid/provider/Settings\$Secure;->getString\(Landroid/content/ContentResolver;Ljava/lang/String;\)Ljava/lang/String;"#,
            smali
        ));
        assert!(!finds(r#"const-string ([pv]\d+), \"bluetooth_address\""#, smali));
        assert!(finds(r"iget-object ([pv]\d+), ([pv]\d+), L(\S+);->ip:Ljava/lang/String;", "iget-object v0, p0, Lcom/a/B;->ip:Ljava/lang/String;"));
        assert!(finds(r"invoke-virtual \{([pv]\d+)\}, Landroid/net/wifi/WifiInfo;->getMacAddress\(\)Ljava/lang/String;", "invoke-virtual {v3}, Landroid/net/wifi/WifiInfo;->getMacAddress()Ljava/lang/String;"));
    }

    #[test]
    fn widened_constructs() {
        assert!(finds("^abc$", "x abc y"));
        assert!(finds(r"\bfoo\b", "xfooy"));
        assert!(finds("a(?=b)", "ac"));
        assert!(finds("a(?!b)", "ab"));
        assert!(finds("(?<=x)a", "a"));
        assert!(finds(r"(a)\1", "ab"));
        assert!(finds("a.b", "a\nb"));
        assert!(finds("a*+b", "aab"));
        assert!(finds("a+?b", "aab"));
        assert!(finds(r"(?<n>a)\k<n>", "ab"));
        assert!(finds(r"\p{L}x", "éx"));
        assert!(finds("a{2,500}", "aa"));
        assert!(finds(r"\Qa.b\E", "a.b"));
        assert!(!finds(r"\Qa.b\E", "axb"));
        assert!(finds(r"\Qab\E{2}", "abb"));
        assert!(finds("(?s)a.b", "a\nb"));
        assert!(finds("(?m:^a)", "b\na"));
    }

    #[test]
    fn escapes_and_classes() {
        assert!(finds(r"\x41B\0103\cJ\t", "ABC\n\t"));
        assert!(finds(r"\x{1F600}", "\u{1F600}"));
        assert!(finds(r"😀", "\u{1F600}"));
        assert!(finds("[a-c]x", "bx"));
        assert!(!finds("[a-c]x", "dx"));
        assert!(finds("[^;]+;", "abc;"));
        assert!(!finds("[^a]", "aaa"));
        assert!(finds(r"[\w$]+", "a$b"));
        assert!(finds(r"[a\-z]", "-"));
        assert!(finds("[a-]", "-"));
        assert!(finds("[-a]", "-"));
        assert!(finds(r"[\]]", "]"));
        assert!(finds(r"[^\d]", "x"));
        assert!(finds(r"\S\s\W\D", "a\u{0B}-x"));
        assert!(finds(r"[\p{Lu}]", "\n"));
        assert!(finds("a]b}", "a]b}"));
        assert!(finds("a|b", "b"));
        assert!(finds("", "anything"));
        assert!(finds("()", ""));
    }

    /// Found by differential fuzzing against Android's java.util.regex (ICU).
    #[test]
    fn icu_differences() {
        // ICU's `.` in DOTALL mode consumes CR LF as one character.
        assert!(finds("a.b", "a\r\nb"));
        assert!(finds("(?s)x..y", "x\r\n\r\ny"));
        assert!(finds("x.{2}y", "x\r\n\r\ny"));
        assert!(finds(r"[\p{L}]b", "\r\nb"));
        assert!(!finds("a.b", "ab"));
        // ICU reads a set starting with `[:` as a POSIX-style property.
        assert!(finds("[:xdigit:]", "7"));
        assert!(finds(r"\s[:xdigit:]", " f"));
        assert!(finds("[:^L:]", "\n"));
        assert!(finds("[^:L:]", "x"));
        assert!(finds("[:]", "q"));
        assert!(finds("[a:]", ":"));
        assert!(!finds("[a:]", "q"));
        // `\cX`: kept where Java (X ^ 0x40) and ICU (X & 0x1F) agree.
        assert!(finds(r"\cA\c@\c[\c_", "\u{1}\u{0}\u{1B}\u{1F}"));
    }

    fn finds_ci(java: &str, text: &str) -> bool {
        let relaxed = relax_case_insensitive(java).unwrap_or_else(|e| panic!("{java}: {e:?}"));
        regex::bytes::RegexBuilder::new(&relaxed)
            .case_insensitive(true)
            .build()
            .unwrap_or_else(|e| panic!("{java} -> {relaxed}: {e}"))
            .is_match(text.as_bytes())
    }

    /// The find-in-files search: CASE_INSENSITIVE patterns, literal ones quoted as ExtGrep does,
    /// whole words wrapped in `\b`.
    #[test]
    fn case_insensitive() {
        assert!(finds_ci("getdeviceid", "invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getDeviceId()"));
        assert!(finds_ci(r"\bGETDEVICEID\b", "->getDeviceId()"));
        assert!(!finds_ci("getdeviceids", "->getDeviceId()"));
        assert!(finds_ci(r"const-string v0, \x22", "CONST-STRING V0, \""));
        assert!(finds_ci(r"\.method public", ".METHOD PUBLIC"));
        assert!(finds_ci(r"Lcom/A/\$B;->x\(\)", "lcom/a/$b;->X()"));
        assert!(finds_ci("[a-c]x", "BX"));
        assert!(finds_ci("[A-C]x", "bX"));
        assert!(!finds_ci("[a-c]x", "dx"));
        assert!(finds_ci(r"\x41\u0062\0103", "abc"));
        assert!(finds_ci("(a)\\1", "aA"));
        assert!(finds_ci("(?i)ab", "AB"));
        assert!(finds_ci("(?-i)ab", "AB"));
        assert!(finds_ci("(?-i:a)b", "AB"));
        // Negated sets match anything: ICU may build their case closure differently.
        assert!(finds_ci("[^a]", "A"));
        assert!(finds_ci(r"[\Wx]", "A"));
        assert!(finds_ci(r"\W", "a"));
        assert!(finds_ci(r"\D\S", "1 "));
        assert!(finds_ci(r"[\d]", "7"));
        assert!(!finds_ci(r"[\d]", "x"));
    }

    #[test]
    fn case_insensitive_refused() {
        for java in ["ß", "straße", r"\u00DF", r"\x{212A}", "[ſ]", r"[a-\u00FF]", r"\Qé\E", "\\é", r"\0337", "(?x)a b", "(?x:a)", "[a"] {
            assert!(relax_case_insensitive(java).is_err(), "{java} should be refused, got {:?}", relax_case_insensitive(java));
        }
        // The same patterns, case-sensitive.
        for java in ["ß", r"\u00DF", "[ſ]", r"\Qé\E"] {
            assert!(relax(java).is_ok(), "{java}");
        }
    }

    #[test]
    fn refused() {
        for java in [
            "(?i)abc", "(?x)a b", "(?i:a)", "[a[b]]", "[a&&b]", "[a--b]", "[]a]", r"\N{SPACE}",
            r"\l", r"[\Qa\E]", r"[\b]", "*a", "a{", "a{2,1}", "(a", "a)", "[abc", r"\", r"\uD800",
            r"\0", r"\xZZ", r"\ca", r"\cz", r"\c1", r#"\c""#, r"[\ca-\cz]", r"a\c!",
        ] {
            assert!(relax(java).is_err(), "{java} should be refused, got {:?}", relax(java));
        }
    }
}

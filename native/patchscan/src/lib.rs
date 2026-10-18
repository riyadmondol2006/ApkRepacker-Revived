//! libpatchscan: the native side of com.riyadm.patchengine.NativeScan.
//!
//! One call per patch rule (or batch of rules): which of the target files can contain a match.
//! The Java rule then runs on those files only, so it still decides every match and writes every
//! replacement; this library only skips files that certainly don't match. See relax.rs for how a
//! Java regex is widened so that "no match here" is always true for Java too.

pub mod relax;
pub mod scan;

use std::panic::{catch_unwind, AssertUnwindSafe};

use jni::objects::{JClass, JObject, JObjectArray, JString};
use jni::sys::{jbooleanArray, jint};
use jni::JNIEnv;

/// `NativeScan.scan(root, files, regexes, lineSets, threads)`: a boolean per file (true: may
/// match), or null when a pattern can't be relaxed safely or anything goes wrong, and the caller
/// scans every file in Java.
#[no_mangle]
pub extern "system" fn Java_com_riyadm_patchengine_NativeScan_scan<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: JString<'local>,
    files: JObjectArray<'local>,
    regexes: JObjectArray<'local>,
    line_sets: JObjectArray<'local>,
    threads: jint,
) -> jbooleanArray {
    let result = catch_unwind(AssertUnwindSafe(|| -> Option<jbooleanArray> {
        let root = java_string(&mut env, &root)?;
        let files = string_array(&mut env, &files)?;
        let regexes = string_array(&mut env, &regexes)?;
        let count = env.get_array_length(&line_sets).ok()?;
        let mut sets = Vec::with_capacity(count as usize);
        for i in 0..count {
            let set = JObjectArray::from(env.get_object_array_element(&line_sets, i).ok()?);
            sets.push(string_array(&mut env, &set)?);
            env.delete_local_ref(set).ok()?;
        }
        let queries = scan::Queries::new(&regexes, &sets).ok()?;
        let found = scan::candidates(&root, &files, &queries, threads.max(1) as usize);
        let flags: Vec<u8> = found.into_iter().map(u8::from).collect();
        let array = env.new_boolean_array(flags.len() as i32).ok()?;
        env.set_boolean_array_region(&array, 0, &flags).ok()?;
        Some(array.into_raw())
    }));
    match result {
        Ok(Some(array)) => array,
        _ => {
            // A failed JNI call may have left an exception pending; the caller only sees null.
            let _ = env.exception_clear();
            std::ptr::null_mut()
        }
    }
}

fn java_string(env: &mut JNIEnv, s: &JString) -> Option<String> {
    if s.is_null() {
        return None;
    }
    let java = env.get_string(s).ok()?;
    java.to_str().ok().map(|s| s.to_owned())
}

fn string_array(env: &mut JNIEnv, array: &JObjectArray) -> Option<Vec<String>> {
    if array.is_null() {
        return None;
    }
    let count = env.get_array_length(array).ok()?;
    let mut out = Vec::with_capacity(count as usize);
    for i in 0..count {
        let element: JObject = env.get_object_array_element(array, i).ok()?;
        let s = JString::from(element);
        out.push(java_string(env, &s)?);
        env.delete_local_ref(s).ok()?;
    }
    Some(out)
}

package org.javacs.example;

// This file is intentionally not Unicode. It exists to test handling
// non-unicode files.
//
// This file should be encoded using the Windows-1252 encoding

class EncodingWindows1252 {
	/**
	 * This property tests for the support of non-unicode strings such as "ü".
	 * Also this comment tests for support in doc comments.
	 */
	String uWithUmlaut = "ü";
}
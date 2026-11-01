package com.nexadesk.support;

/** Minimal real file contents for upload tests. */
public final class TestFiles {

    /** PNG signature followed by the start of an IHDR chunk: enough for content-based detection. */
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    /** "MZ" header of a Windows executable. */
    public static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

    private TestFiles() {
    }
}

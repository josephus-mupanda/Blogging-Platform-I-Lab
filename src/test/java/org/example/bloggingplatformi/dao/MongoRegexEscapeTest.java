package org.example.bloggingplatformi.dao;

import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The subtree filter escapes the path prefix by hand rather than with
 * Pattern.quote, because MongoDB only derives index bounds from a plain
 * anchored prefix. These check the hand-rolled escaping is still safe.
 */
class MongoRegexEscapeTest {

    @Test
    @DisplayName("a real materialized path passes through unchanged, so MongoDB sees a plain prefix")
    void pathUnchanged() {
        String path = "/661f0c2b9a1e4d0012ab34cd/661f0c2b9a1e4d0012ab34ce/";
        assertEquals(path, MongoCommentDao.escapeRegex(path));
    }

    @Test
    @DisplayName("every regex metacharacter is matched literally")
    void metacharactersAreLiteral() {
        String nasty = "/a.b*c+d?e(f)g[h]i{j}k|l^m$n\\o/";
        Pattern pattern = Pattern.compile("^" + MongoCommentDao.escapeRegex(nasty));
        assertTrue(pattern.matcher(nasty + "child/").find());
        assertFalse(pattern.matcher("/aXb*c+d?e(f)g[h]i{j}k|l^m$n\\o/").find(), "'.' must not match any char");
    }
}

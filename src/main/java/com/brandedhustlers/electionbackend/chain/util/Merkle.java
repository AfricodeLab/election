package com.brandedhustlers.electionbackend.chain.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class Merkle {

    private static final Charset UTF8 = StandardCharsets.UTF_8;

    // Leaf: SHA256(voteId|candidateId|electionId|orgId)
    public static byte[] leaf(UUID voteId, UUID candidateId, UUID electionId, UUID orgId) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(voteId.toString().getBytes(UTF8));
            md.update(candidateId.toString().getBytes(UTF8));
            md.update(electionId.toString().getBytes(UTF8));
            md.update(orgId.toString().getBytes(UTF8));
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Build Merkle root from a list of leaves (already hashed)
    public static String rootHex(List<byte[]> leaves) {
        if (leaves.isEmpty()) {
            return hex(zero32()); // or return 32 zeros
        }
        var level = new ArrayList<>(leaves);
        try {
            var md = MessageDigest.getInstance("SHA-256");
            while (level.size() > 1) {
                var next = new ArrayList<byte[]>();
                for (int i = 0; i < level.size(); i += 2) {
                    byte[] left = level.get(i);
                    byte[] right = (i + 1 < level.size()) ? level.get(i + 1) : left; // duplicate if odd
                    md.reset();
                    md.update(left);
                    md.update(right);
                    next.add(md.digest());
                }
                level = next;
            }
            return hex(level.get(0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }

    private static byte[] zero32() {
        return new byte[32];
    }
}

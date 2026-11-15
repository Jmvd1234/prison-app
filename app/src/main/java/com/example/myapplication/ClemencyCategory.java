package com.example.myapplication;

/** Explicit, record-confirmed Section 4(A)(3)-(8) categories; never inferred from penalties. */
public enum ClemencyCategory {
    NONE(0), SINGLE_GENERAL(10), ARTICLE_70(13), SINGLE_HEINOUS(15),
    SPECIFIED_OFFENCES(18), MULTIPLE_PERPETUAL(20), REDUCED_DEATH(25);

    public final int years;
    ClemencyCategory(int years) { this.years = years; }

    public static ClemencyCategory parse(String value) {
        if (value == null || value.isEmpty()) return NONE;
        try { return valueOf(value); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unrecognized executive clemency category.");
        }
    }
}

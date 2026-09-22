package com.ariadne.extraction;

public record RemoteFund(
        String fundCode,
        String fundName,
        String fundType,
        String pinyinAbbreviation,
        String pinyinFullName
) {}

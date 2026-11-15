package com.vitalis.demo.dto.response;

public record BottleReportDTO(long fullBottlesSold, long brokenBottles,
                              long brokenValidBottles, long brokenExpiredBottles) {}

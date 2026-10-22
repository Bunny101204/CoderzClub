package com.coderzclub.service;

public record LanguageCapability(
    int id,
    String displayName,
    boolean enabled,
    boolean standardPerCase,
    boolean batchStdinProgram,
    boolean functionHarnessBatch,
    boolean liveProviderVerified
) {}

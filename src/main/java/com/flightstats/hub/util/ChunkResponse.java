package com.flightstats.hub.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ChunkResponse {
    private boolean success;
    private String message;
}

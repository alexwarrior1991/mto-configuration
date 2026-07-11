package com.alejandro.mtoconfiguration.masterdata.messaging;

import java.util.Map;

public record MasterDataChangedEvent(
        String entityName,
        String entityId,
        MasterDataOperation operation,
        Map<String, Object> values
) {
}

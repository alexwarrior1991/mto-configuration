package com.alejandro.mtoconfiguration.masterdata.messaging;

import java.util.Map;

public interface MasterDataEntityPayloadMapper<T> {

    Class<T> supportedType();

    Map<String, Object> toPayload(T entity);
}

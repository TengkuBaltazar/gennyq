package life.genny.qwandaq.serialization.baseentityattribute;

import java.io.IOException;

import org.infinispan.protostream.MessageMarshaller;

public class BaseEntityAttributeKeyMessageMarshaller implements MessageMarshaller<BaseEntityAttributeKey> {

	@Override
	public Class<BaseEntityAttributeKey> getJavaClass() {
		return BaseEntityAttributeKey.class;
	}

	@Override
	public String getTypeName() {
		return "life.genny.qwandaq.serialization.baseentityattribute.BaseEntityAttributeKey";
	}

	@Override
	public BaseEntityAttributeKey readFrom(ProtoStreamReader reader) throws IOException {
		String productCode = reader.readString("realm");
		String baseEntityCode = reader.readString("baseentitycode");
		String attributeCode = reader.readString("attributecode");
		return new BaseEntityAttributeKey(productCode, baseEntityCode, attributeCode);
	}

	@Override
	public void writeTo(ProtoStreamWriter writer, BaseEntityAttributeKey beak) throws IOException {
		writer.writeString("realm", beak.getRealm());
		writer.writeString("baseentitycode", beak.getBaseEntityCode());
		writer.writeString("attributecode", beak.getAttributeCode());
	}

}

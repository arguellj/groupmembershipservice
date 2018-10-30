package groupmembershipservice;

import java.io.*;

public class Utils {

    private Utils() {}

    public static byte[] serializeObject(Object obj) throws IOException {
        // Serialize to a byte array
        ByteArrayOutputStream bStream = new ByteArrayOutputStream();
        ObjectOutput oo = new ObjectOutputStream(bStream);
        oo.writeObject(obj);
        oo.close();

        byte[] serializedMessage = bStream.toByteArray();
        return serializedMessage;
    }

    public static Object deserializeObject(byte[] serializedMessage) throws IOException, ClassNotFoundException {
        ObjectInputStream iStream = new ObjectInputStream(new ByteArrayInputStream(serializedMessage));
        Object object = iStream.readObject();
        iStream.close();
        return object;
    }
}

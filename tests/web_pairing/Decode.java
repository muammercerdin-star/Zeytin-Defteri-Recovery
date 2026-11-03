import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

/** Üretici JS qrcode-generator; bağımsız okuyucu ZXing 3.5.3. */
public final class Decode {
    public static void main(String[] args) throws Exception {
        JSONArray rows = new JSONArray(new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8"));
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            int size = row.getInt("size");
            JSONArray pixels = row.getJSONArray("pixels");
            int[] rgb = new int[pixels.length()];
            for (int p = 0; p < rgb.length; p++) rgb[p] = pixels.getInt(p);
            String decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(size, size, rgb)))).getText();
            if (!decoded.equals(row.getString("text"))) throw new AssertionError("Logolu QR çözülemedi: " + i);
        }
        System.out.println("PASS " + rows.length() + " bağımsız ZXing logolu QR çözümleme");
    }
}

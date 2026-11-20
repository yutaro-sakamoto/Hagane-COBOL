import java.io.UnsupportedEncodingException;
import org.haganecobol.libcobj.*;
import org.haganecobol.libcobj.common.*;
import org.haganecobol.libcobj.data.*;
import org.haganecobol.libcobj.exceptions.*;
import org.haganecobol.libcobj.termio.*;
import org.haganecobol.libcobj.call.*;
import org.haganecobol.libcobj.file.*;
import org.haganecobol.libcobj.ui.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.io.FileNotFoundException;

public class wait implements CobolRunnable {
  private static Map<String, BufferedReader> namedPipeReaders = new HashMap<>();
  @Override
  public int run(CobolDataStorage... argStorages) {
    AbstractCobolField namedPipeField = CobolModule.getCurrentModule().cob_procedure_parameters.get(0);
    AbstractCobolField valueField = CobolModule.getCurrentModule().cob_procedure_parameters.get(1);
    String namedPipe = namedPipeField.getString().trim();
    String value = valueField.getString().trim();

    while(true) {
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(namedPipe)))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(value)) {
                    return 0;
                } else {
                    Thread.sleep(200);
                    continue;
                }
            }
        } catch (FileNotFoundException e) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
                Thread.currentThread().interrupt();
                return -1;
            }
            continue;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
            return -1;
        }
    }
  }

  @Override
  public void cancel() {
    return;
  }

  @Override
  public boolean isActive() {
    return false;
  }
}
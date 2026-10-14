package rehab.rules

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test

/** Outil, pas un test : ignoré sauf `-Prehab.anonymize.in=… -Prehab.anonymize.out=…` (voir CaptureAnonymizer). */
class AnonymizeCaptures {
    @Test fun run() {
        val inDir = System.getProperty("rehab.anonymize.in").orEmpty()
        val outDir = System.getProperty("rehab.anonymize.out").orEmpty()
        assumeTrue(inDir.isNotEmpty() && outDir.isNotEmpty(), "anonymisation désactivée")
        File(outDir).mkdirs()
        File(inDir).listFiles { f -> f.extension == "json" }!!.forEach { f ->
            File(outDir, f.name).writeText(SnapshotJson.encode(CaptureAnonymizer.anonymize(SnapshotJson.decode(f.readText()))))
        }
    }
}

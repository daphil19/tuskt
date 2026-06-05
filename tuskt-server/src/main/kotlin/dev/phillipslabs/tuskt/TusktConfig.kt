package dev.phillipslabs.tuskt

import dev.phillipslabs.tuskt.store.FileSystemUploadStore
import dev.phillipslabs.tuskt.store.TusktStore
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute

public class TusktConfig {
    // TODO make any of these constructor args?
    public var basePath: String = "/files"
    public var storagePath: Path = Path("files").absolute()
    public var tusktStore: TusktStore = FileSystemUploadStore(storagePath)
}

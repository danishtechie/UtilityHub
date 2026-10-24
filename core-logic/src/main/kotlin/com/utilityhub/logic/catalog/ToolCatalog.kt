package com.utilityhub.logic.catalog

enum class ToolCategory(val title: String) {
    IMAGES("Images"),
    VIDEO_AUDIO("Video & Audio"),
    PDF("PDF & Documents"),
    FILES("Files & ZIP"),
    TEXT("Text Tools"),
    EVERYDAY("Everyday"),
}

enum class ToolStatus { AVAILABLE, PLANNED }

/**
 * @param metered true when the tool produces a new file and therefore counts against the free allowance.
 *                Lightweight text / inspector tools are intentionally free (they cost nothing to run).
 */
data class ToolMeta(
    val id: String,
    val title: String,
    val description: String,
    val category: ToolCategory,
    val status: ToolStatus,
    val metered: Boolean = false,
    val keywords: List<String> = emptyList(),
)

object ToolCatalog {
    private fun ready(id: String, title: String, description: String, category: ToolCategory, metered: Boolean = false, vararg keywords: String) =
        ToolMeta(id, title, description, category, ToolStatus.AVAILABLE, metered, keywords.toList())

    private fun planned(id: String, title: String, category: ToolCategory, vararg keywords: String) =
        ToolMeta(id, title, "Planned - not available in this version.", category, ToolStatus.PLANNED, false, keywords.toList())

    val all: List<ToolMeta> = listOf(
        // ---- Images
        ready("image_resize", "Image Resizer", "Change image dimensions and keep the aspect ratio.", ToolCategory.IMAGES, true, "resize", "scale", "dimensions", "width", "height"),
        ready("image_compress", "Photo Compressor", "Shrink photo file size with an adjustable quality level.", ToolCategory.IMAGES, true, "compress", "reduce", "size", "jpeg"),
        ready("image_convert", "Image Format Converter", "Convert between JPEG, PNG and WebP.", ToolCategory.IMAGES, true, "convert", "png", "jpg", "webp", "format"),
        ready("image_rotate_flip", "Rotate & Flip", "Rotate by 90° steps and flip horizontally or vertically.", ToolCategory.IMAGES, true, "rotate", "flip", "mirror"),
        ready("image_grayscale", "Grayscale Converter", "Turn a colour image into black and white.", ToolCategory.IMAGES, true, "grayscale", "black", "white", "monochrome"),
        ready("image_to_pdf", "Images to PDF", "Combine one or more images into a PDF.", ToolCategory.IMAGES, true, "pdf", "convert", "photos", "pages"),
        ready("image_bg_changer", "Background Color Changer", "Replace image background with solid White, Blue, Red, Grey, or custom colors.", ToolCategory.IMAGES, true, "background", "color", "remove", "white", "passport", "change"),
        ready("image_metadata", "Image Metadata Viewer", "Show dimensions, format and EXIF information.", ToolCategory.IMAGES, false, "exif", "metadata", "gps", "camera", "inspector", "dimensions"),
        ready("qr_generate", "QR Code Generator", "Create a QR code image from text or a link.", ToolCategory.IMAGES, true, "qr", "code", "link", "wifi"),
        ready("qr_scan", "QR / Barcode Reader", "Read a QR code or barcode from a saved image.", ToolCategory.IMAGES, false, "qr", "scan", "barcode", "decode"),
        ready("image_crop", "Image Cropper", "Crop to a preset ratio and slide the crop into place.", ToolCategory.IMAGES, true, "crop", "ratio", "square"),
        ready("image_watermark", "Image Watermark", "Add a text watermark with position, size and opacity.", ToolCategory.IMAGES, true, "watermark", "text", "logo"),
        ready("image_border", "Border & Padding", "Add a coloured border around a photo.", ToolCategory.IMAGES, true, "border", "padding", "frame"),
        ready("image_color_picker", "Colour Picker", "Find the most common colours in a photo.", ToolCategory.IMAGES, false, "color", "colour", "palette", "hex"),
        ready("image_batch_resize", "Batch Resizer", "Resize many images in one operation.", ToolCategory.IMAGES, true, "batch", "resize", "many"),
        ready("image_batch_compress", "Batch Compressor", "Compress many photos in one operation.", ToolCategory.IMAGES, true, "batch", "compress", "many"),
        ready("passport_photo", "Passport Photo Maker", "Passport-size photos with white background on a printable sheet.", ToolCategory.IMAGES, true, "passport", "id", "photo", "print", "visa", "exam"),
        ready("image_collage", "Collage Maker", "Combine 2-4 photos in a simple layout.", ToolCategory.IMAGES, true, "collage", "combine", "grid"),
        ready("barcode_generate", "Barcode Generator", "Create Code 128, EAN-13, UPC-A or Code 39 barcodes.", ToolCategory.IMAGES, true, "barcode", "ean", "upc", "code128"),
        ready("image_base64", "Image to Base64", "Convert a small image to Base64 text.", ToolCategory.IMAGES, false, "base64", "encode", "image"),
        // ---- Video & audio
        ready("base64_to_image", "Base64 to Image", "Turn Base64 text back into an image file.", ToolCategory.IMAGES, true, "base64", "decode", "image"),
        ready("video_to_audio", "Video to Audio", "Extract the AAC audio track of a video into an .m4a file.", ToolCategory.VIDEO_AUDIO, true, "extract", "mp3", "audio", "m4a", "video"),
        ready("video_info", "Media Information", "Duration, resolution, bitrate and tracks of a video or audio file.", ToolCategory.VIDEO_AUDIO, false, "info", "duration", "resolution", "codec", "inspector"),
        ready("video_trim", "Video Trimmer", "Cut a section of a video without re-encoding.", ToolCategory.VIDEO_AUDIO, true, "trim", "cut", "video", "clip"),
        planned("video_compress", "Video Compressor", ToolCategory.VIDEO_AUDIO, "compress", "reduce"),
        planned("video_gif", "Video to GIF", ToolCategory.VIDEO_AUDIO, "gif"),
        ready("video_thumbnail", "Video Thumbnail", "Save a still frame from a video.", ToolCategory.VIDEO_AUDIO, true, "thumbnail", "frame", "still", "screenshot"),
        ready("audio_trim", "Audio Trimmer", "Cut a section of an AAC audio file.", ToolCategory.VIDEO_AUDIO, true, "trim", "cut", "audio", "m4a"),
        planned("audio_convert", "Audio Converter", ToolCategory.VIDEO_AUDIO, "convert", "mp3", "wav"),
        // ---- PDF
        ready("pdf_merge", "Merge PDFs", "Join several PDFs into one document.", ToolCategory.PDF, true, "combine", "join", "merge"),
        ready("pdf_split", "Split PDF", "Split a PDF into files of N pages each.", ToolCategory.PDF, true, "split", "separate", "pages"),
        ready("pdf_extract", "Extract Pages", "Create a new PDF from selected pages (reorder supported).", ToolCategory.PDF, true, "extract", "select", "pages", "reorder"),
        ready("pdf_delete_pages", "Delete Pages", "Save a copy of a PDF without the pages you choose.", ToolCategory.PDF, true, "delete", "remove", "pages"),
        ready("pdf_info", "PDF Information", "Page count, size, title and author of a PDF.", ToolCategory.PDF, false, "info", "pages", "count", "inspector"),
        ready("pdf_text", "PDF Text Extractor", "Extract selectable text from a PDF (no OCR).", ToolCategory.PDF, false, "text", "extract", "copy"),
        planned("pdf_compress", "Compress PDF", ToolCategory.PDF, "compress", "reduce"),
        ready("pdf_to_image", "PDF to Images", "Turn PDF pages into PNG or JPEG images.", ToolCategory.PDF, true, "image", "png", "jpg", "pages"),
        ready("pdf_watermark", "PDF Watermark", "Stamp text across every page of a PDF.", ToolCategory.PDF, true, "watermark", "stamp", "confidential"),
        ready("pdf_page_numbers", "Add Page Numbers", "Number the pages of a PDF.", ToolCategory.PDF, true, "numbers", "footer", "pages"),
        ready("pdf_blank", "Blank PDF", "Create an empty PDF with the page size you choose.", ToolCategory.PDF, true, "blank", "new", "empty"),
        ready("text_to_pdf", "Text to PDF", "Turn typed or pasted text into a PDF.", ToolCategory.PDF, true, "text", "document", "convert"),
        planned("ocr_scanner", "OCR Scanner", ToolCategory.PDF, "ocr", "scan", "text"),
        // ---- Files
        ready("zip_create", "ZIP Maker", "Pack files into a ZIP archive.", ToolCategory.FILES, true, "zip", "archive", "compress"),
        ready("zip_extract", "ZIP Extractor", "Safely unpack a ZIP archive (path-traversal and bomb protection).", ToolCategory.FILES, true, "unzip", "extract", "archive"),
        ready("file_hash", "File Hash Generator", "MD5, SHA-1 and SHA-256 checksums for any file.", ToolCategory.FILES, false, "checksum", "sha", "md5", "integrity", "verify"),
        ready("file_info", "File Information", "Name, size, type and modified date of a file.", ToolCategory.FILES, false, "metadata", "size", "type", "inspector"),
        ready("file_rename", "Batch Renamer", "Create renamed copies of many files at once.", ToolCategory.FILES, true, "rename", "batch", "files", "numbering"),
        ready("duplicate_files", "Duplicate File Finder", "Find identical files among the ones you pick.", ToolCategory.FILES, false, "duplicate", "same", "hash"),
        ready("csv_viewer", "CSV Viewer", "Preview a CSV file as a table.", ToolCategory.FILES, false, "csv", "table", "spreadsheet"),
        // ---- Text
        ready("text_stats", "Word & Character Counter", "Count words, characters and lines.", ToolCategory.TEXT, false, "count", "words", "characters", "lines"),
        ready("text_case", "Text Case Converter", "UPPER, lower, Title and Sentence case.", ToolCategory.TEXT, false, "uppercase", "lowercase", "title"),
        ready("text_dedupe", "Remove Duplicate Lines", "Keep the first occurrence of each line.", ToolCategory.TEXT, false, "unique", "duplicates", "lines"),
        ready("text_sort", "Sort Lines", "Sort lines alphabetically or numerically.", ToolCategory.TEXT, false, "order", "alphabetical", "lines"),
        ready("json_format", "JSON Formatter & Validator", "Validate, pretty-print or minify JSON.", ToolCategory.TEXT, false, "json", "pretty", "minify", "validate"),
        ready("base64_text", "Base64 Encoder / Decoder", "Encode or decode text as Base64.", ToolCategory.TEXT, false, "base64", "encode", "decode"),
        ready("url_codec", "URL Encoder / Decoder", "Percent-encode or decode text.", ToolCategory.TEXT, false, "url", "percent", "encode", "decode"),
        ready("password_gen", "Password Generator", "Strong random passwords using the device's secure RNG.", ToolCategory.TEXT, false, "password", "secure", "random"),
        ready("uuid_gen", "UUID Generator", "Generate random version-4 UUIDs.", ToolCategory.TEXT, false, "uuid", "guid", "id"),
        ready("random_string", "Random String Generator", "Cryptographically secure random strings.", ToolCategory.TEXT, false, "random", "token", "string"),
        ready("regex_tester", "Regex Tester", "Test a regular expression with a built-in time limit.", ToolCategory.TEXT, false, "regex", "pattern", "match"),
        ready("timestamp", "Timestamp Converter", "Epoch time ⇄ readable date.", ToolCategory.TEXT, false, "epoch", "unix", "date", "time"),
        ready("markdown_preview", "Markdown Preview", "Write Markdown and see it formatted.", ToolCategory.TEXT, false, "markdown", "preview", "md"),
        ready("notes", "Notes & Scratchpad", "Private notes stored only on this device.", ToolCategory.TEXT, false, "notes", "scratchpad", "memo", "pad"),
        // ---- Everyday
        ready("unit_convert", "Unit Converter", "Length, mass, time, temperature and digital storage.", ToolCategory.EVERYDAY, false, "convert", "units", "length", "weight", "temperature", "storage"),
        ready("percentage", "Percentage Calculator", "Percent of, what percent, and percent change.", ToolCategory.EVERYDAY, false, "percent", "calculate", "discount"),
        ready("age_calc", "Age Calculator", "Exact age in years, months and days.", ToolCategory.EVERYDAY, false, "age", "birthday", "date"),
        ready("date_diff", "Date Difference", "Time between two dates.", ToolCategory.EVERYDAY, false, "days", "between", "date"),
        ready("stopwatch", "Stopwatch & Timer", "Stopwatch with laps and a countdown timer.", ToolCategory.EVERYDAY, false, "stopwatch", "timer", "countdown", "laps"),
        ready("calculator", "Calculator", "Calculator with brackets, powers and functions.", ToolCategory.EVERYDAY, false, "calculator", "math", "sqrt"),
        ready("text_to_speech", "Text to Speech", "Read text aloud with your device's voice.", ToolCategory.EVERYDAY, false, "speech", "read", "voice", "tts"),
    )

    fun byId(id: String): ToolMeta? = all.firstOrNull { it.id == id }
    fun byCategory(category: ToolCategory): List<ToolMeta> = all.filter { it.category == category }
}

object ToolSearch {
    /** AND-semantics over whitespace-separated words; available tools first, then title > keyword > description matches. */
    fun search(tools: List<ToolMeta>, query: String): List<ToolMeta> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return tools
        return tools.mapNotNull { tool ->
            val title = tool.title.lowercase()
            var total = 0
            for (w in words) {
                val score = when {
                    title.split(' ').any { it.startsWith(w) } -> 6
                    title.contains(w) -> 4
                    tool.keywords.any { it.lowercase().contains(w) } -> 3
                    tool.description.lowercase().contains(w) -> 1
                    else -> 0
                }
                if (score == 0) return@mapNotNull null
                total += score
            }
            tool to total
        }.sortedWith(
            compareBy<Pair<ToolMeta, Int>> { it.first.status.ordinal }   // available tools always before planned ones
                .thenByDescending { it.second }
                .thenBy { tools.indexOf(it.first) },   // ties keep catalog order (primary tools first)
        ).map { it.first }
    }
}

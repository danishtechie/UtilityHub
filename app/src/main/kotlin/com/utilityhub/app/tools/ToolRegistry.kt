package com.utilityhub.app.tools

import com.utilityhub.app.core.FileTool
import com.utilityhub.app.tools.files.BatchRenameTool
import com.utilityhub.app.tools.files.CsvViewerTool
import com.utilityhub.app.tools.files.DuplicateFinderTool
import com.utilityhub.app.tools.files.FileHashTool
import com.utilityhub.app.tools.files.FileInfoTool
import com.utilityhub.app.tools.files.ZipCreateTool
import com.utilityhub.app.tools.files.ZipExtractTool
import com.utilityhub.app.tools.form.FormTool
import com.utilityhub.app.tools.form.FormTools
import com.utilityhub.app.tools.image.Base64ToImageTool
import com.utilityhub.app.tools.image.BatchCompressTool
import com.utilityhub.app.tools.image.BatchResizeTool
import com.utilityhub.app.tools.image.BorderTool
import com.utilityhub.app.tools.image.CollageTool
import com.utilityhub.app.tools.image.ColorPickerTool
import com.utilityhub.app.tools.image.CompressTool
import com.utilityhub.app.tools.image.CropTool
import com.utilityhub.app.tools.image.ImageBgChangerTool
import com.utilityhub.app.tools.image.ImageToBase64Tool
import com.utilityhub.app.tools.image.PassportPhotoTool
import com.utilityhub.app.tools.image.WatermarkTool
import com.utilityhub.app.tools.image.ConvertTool
import com.utilityhub.app.tools.image.GrayscaleTool
import com.utilityhub.app.tools.image.ImageMetadataTool
import com.utilityhub.app.tools.image.ImageToPdfTool
import com.utilityhub.app.tools.image.ResizeTool
import com.utilityhub.app.tools.image.RotateFlipTool
import com.utilityhub.app.tools.media.AudioTrimTool
import com.utilityhub.app.tools.media.MediaInfoTool
import com.utilityhub.app.tools.media.VideoThumbnailTool
import com.utilityhub.app.tools.media.VideoTrimTool
import com.utilityhub.app.tools.media.VideoToAudioTool
import com.utilityhub.app.tools.pdf.BlankPdfTool
import com.utilityhub.app.tools.pdf.PdfDeletePagesTool
import com.utilityhub.app.tools.pdf.PdfPageNumbersTool
import com.utilityhub.app.tools.pdf.PdfToImageTool
import com.utilityhub.app.tools.pdf.PdfWatermarkTool
import com.utilityhub.app.tools.pdf.TextToPdfTool
import com.utilityhub.app.tools.pdf.PdfExtractTool
import com.utilityhub.app.tools.pdf.PdfInfoTool
import com.utilityhub.app.tools.pdf.PdfMergeTool
import com.utilityhub.app.tools.pdf.PdfSplitTool
import com.utilityhub.app.tools.pdf.PdfTextTool
import com.utilityhub.app.tools.qr.BarcodeGenerateTool
import com.utilityhub.app.tools.qr.QrGenerateTool
import com.utilityhub.app.tools.qr.QrScanTool
import com.utilityhub.logic.catalog.ToolCatalog
import com.utilityhub.logic.catalog.ToolMeta
import com.utilityhub.logic.catalog.ToolStatus
import com.utilityhub.logic.entitlement.EntitlementPolicy

enum class CustomKind { CALCULATOR, STOPWATCH, NOTES, MARKDOWN, SPEECH }

sealed interface ToolHost {
    data class Form(val tool: FormTool) : ToolHost
    data class Files(val tool: FileTool) : ToolHost
    data object UnitConverter : ToolHost
    data class Custom(val kind: CustomKind) : ToolHost
}

/**
 * Maps catalog ids to real implementations.
 */
object ToolRegistry {
    private val batch = EntitlementPolicy().maxBatchItemsPerOperation

    private val hosts: Map<String, ToolHost> = buildMap {
        put("image_resize", ToolHost.Files(ResizeTool()))
        put("image_compress", ToolHost.Files(CompressTool()))
        put("image_convert", ToolHost.Files(ConvertTool()))
        put("image_rotate_flip", ToolHost.Files(RotateFlipTool()))
        put("image_grayscale", ToolHost.Files(GrayscaleTool()))
        put("image_to_pdf", ToolHost.Files(ImageToPdfTool(batch)))
        put("image_bg_changer", ToolHost.Files(ImageBgChangerTool()))
        put("image_metadata", ToolHost.Files(ImageMetadataTool()))
        put("qr_generate", ToolHost.Files(QrGenerateTool()))
        put("qr_scan", ToolHost.Files(QrScanTool()))
        put("video_to_audio", ToolHost.Files(VideoToAudioTool()))
        put("video_info", ToolHost.Files(MediaInfoTool()))
        put("pdf_merge", ToolHost.Files(PdfMergeTool(20)))
        put("pdf_split", ToolHost.Files(PdfSplitTool()))
        put("pdf_extract", ToolHost.Files(PdfExtractTool()))
        put("pdf_delete_pages", ToolHost.Files(PdfDeletePagesTool()))
        put("pdf_info", ToolHost.Files(PdfInfoTool()))
        put("pdf_text", ToolHost.Files(PdfTextTool()))
        put("zip_create", ToolHost.Files(ZipCreateTool(batch)))
        put("zip_extract", ToolHost.Files(ZipExtractTool()))
        put("file_hash", ToolHost.Files(FileHashTool()))
        put("file_info", ToolHost.Files(FileInfoTool()))
        FormTools.all.forEach { (id, tool) -> put(id, ToolHost.Form(tool)) }
        put("unit_convert", ToolHost.UnitConverter)
        put("image_crop", ToolHost.Files(CropTool()))
        put("image_watermark", ToolHost.Files(WatermarkTool()))
        put("image_border", ToolHost.Files(BorderTool()))
        put("image_color_picker", ToolHost.Files(ColorPickerTool()))
        put("image_batch_resize", ToolHost.Files(BatchResizeTool(batch)))
        put("image_batch_compress", ToolHost.Files(BatchCompressTool(batch)))
        put("passport_photo", ToolHost.Files(PassportPhotoTool()))
        put("image_collage", ToolHost.Files(CollageTool()))
        put("barcode_generate", ToolHost.Files(BarcodeGenerateTool()))
        put("image_base64", ToolHost.Files(ImageToBase64Tool()))
        put("base64_to_image", ToolHost.Files(Base64ToImageTool()))
        put("video_trim", ToolHost.Files(VideoTrimTool()))
        put("video_thumbnail", ToolHost.Files(VideoThumbnailTool()))
        put("audio_trim", ToolHost.Files(AudioTrimTool()))
        put("pdf_to_image", ToolHost.Files(PdfToImageTool()))
        put("pdf_watermark", ToolHost.Files(PdfWatermarkTool()))
        put("pdf_page_numbers", ToolHost.Files(PdfPageNumbersTool()))
        put("pdf_blank", ToolHost.Files(BlankPdfTool()))
        put("text_to_pdf", ToolHost.Files(TextToPdfTool()))
        put("file_rename", ToolHost.Files(BatchRenameTool(100)))
        put("duplicate_files", ToolHost.Files(DuplicateFinderTool(200)))
        put("csv_viewer", ToolHost.Files(CsvViewerTool()))
        put("calculator", ToolHost.Custom(CustomKind.CALCULATOR))
        put("stopwatch", ToolHost.Custom(CustomKind.STOPWATCH))
        put("notes", ToolHost.Custom(CustomKind.NOTES))
        put("markdown_preview", ToolHost.Custom(CustomKind.MARKDOWN))
        put("text_to_speech", ToolHost.Custom(CustomKind.SPEECH))
    }

    fun host(id: String): ToolHost? = hosts[id]

    fun isAvailable(meta: ToolMeta): Boolean = meta.status == ToolStatus.AVAILABLE && hosts.containsKey(meta.id)

    val allTools: List<ToolMeta> get() = ToolCatalog.all

    /** Ids the catalog calls available but that have no implementation. */
    fun unimplementedAvailableIds(): List<String> = ToolCatalog.all.filter { it.status == ToolStatus.AVAILABLE && !hosts.containsKey(it.id) }.map { it.id }
}

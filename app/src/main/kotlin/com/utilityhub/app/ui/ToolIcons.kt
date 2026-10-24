package com.utilityhub.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.utilityhub.logic.catalog.ToolCategory

fun categoryIcon(category: ToolCategory): ImageVector = when (category) {
    ToolCategory.IMAGES -> Icons.Rounded.Image
    ToolCategory.VIDEO_AUDIO -> Icons.Rounded.VideoLibrary
    ToolCategory.PDF -> Icons.Rounded.PictureAsPdf
    ToolCategory.FILES -> Icons.Rounded.Folder
    ToolCategory.TEXT -> Icons.Rounded.TextFields
    ToolCategory.EVERYDAY -> Icons.Rounded.Calculate
}

fun toolIcon(id: String, category: ToolCategory): ImageVector = when (id) {
    "image_compress" -> Icons.Rounded.Compress
    "image_convert" -> Icons.Rounded.Transform
    "image_to_pdf", "pdf_info" -> Icons.Rounded.PictureAsPdf
    "image_metadata", "file_info", "video_info" -> Icons.Rounded.Info
    "qr_generate" -> Icons.Rounded.QrCode2
    "qr_scan" -> Icons.Rounded.QrCodeScanner
    "video_to_audio" -> Icons.Rounded.AudioFile
    "pdf_merge" -> Icons.Rounded.CallMerge
    "pdf_split" -> Icons.Rounded.CallSplit
    "pdf_extract" -> Icons.Rounded.ContentCut
    "pdf_delete_pages" -> Icons.Rounded.DeleteSweep
    "pdf_text", "text_stats" -> Icons.Rounded.Description
    "zip_create" -> Icons.Rounded.Folder
    "zip_extract" -> Icons.Rounded.Unarchive
    "file_hash" -> Icons.Rounded.Fingerprint
    "text_sort" -> Icons.Rounded.Sort
    "json_format", "regex_tester", "base64_text" -> Icons.Rounded.Code
    "url_codec" -> Icons.Rounded.Link
    "password_gen" -> Icons.Rounded.Lock
    "uuid_gen", "random_string" -> Icons.Rounded.Tag
    "timestamp" -> Icons.Rounded.Schedule
    "unit_convert" -> Icons.Rounded.SwapHoriz
    "percentage" -> Icons.Rounded.Calculate
    "age_calc" -> Icons.Rounded.Cake
    "date_diff" -> Icons.Rounded.DateRange
    else -> if (category == ToolCategory.FILES) Icons.Rounded.InsertDriveFile else if (id.startsWith("image")) Icons.Rounded.Image else Icons.Rounded.Build
}

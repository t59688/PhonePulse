package com.aizeek.phonepulse.companion

import androidx.core.content.FileProvider

// A distinct component prevents Android from reusing the update provider for this authority.
class CompanionFileProvider : FileProvider()

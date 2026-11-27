package com.dskmusic.pixa.ml

import android.content.res.AssetManager

/** Puente JNI a ncnn (ver cpp/pixasr.cpp). Un handle por modelo cargado; 0 si falla. */
internal object NcnnSr {
    init {
        System.loadLibrary("pixasr")
    }

    external fun create(assets: AssetManager, param: String, bin: String, gpu: Boolean): Long

    /** [px] ARGB de w*h; devuelve (w*scale)*(h*scale) ARGB o null si falla. */
    external fun run(handle: Long, px: IntArray, w: Int, h: Int, scale: Int): IntArray?

    external fun destroy(handle: Long)
}

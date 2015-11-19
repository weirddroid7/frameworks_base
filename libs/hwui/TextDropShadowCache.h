/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_HWUI_TEXT_DROP_SHADOW_CACHE_H
#define ANDROID_HWUI_TEXT_DROP_SHADOW_CACHE_H

#include <GLES2/gl2.h>

#include <SkPaint.h>

#include <utils/LruCache.h>
#include <utils/String16.h>

#include "font/Font.h"
#include "Texture.h"

namespace android {
namespace uirenderer {

class Caches;
class FontRenderer;

struct ShadowText {
    ShadowText(): glyphCount(0), radius(0.0f), textSize(0.0f), typeface(nullptr),
            flags(0), italicStyle(0.0f), scaleX(0), text(nullptr), positions(nullptr) {
    }

    // len is the number of bytes in text
    ShadowText(const SkPaint* paint, float radius, uint32_t glyphCount, const char* srcText,
            const float* positions):
            glyphCount(glyphCount), radius(radius), positions(positions) {
        // TODO: Propagate this through the API, we should not cast here
        text = (const char16_t*) srcText;

        textSize = paint->getTextSize();
        typeface = paint->getTypeface();

        flags = 0;
        if (paint->isFakeBoldText()) {
            flags |= Font::kFakeBold;
        }

        italicStyle = paint->getTextSkewX();
        scaleX = paint->getTextScaleX();
    }

    ~ShadowText() {
    }

    hash_t hash() const;

    static int compare(const ShadowText& lhs, const ShadowText& rhs);

    bool operator==(const ShadowText& other) const {
        return compare(*this, other) == 0;
    }

    bool operator!=(const ShadowText& other) const {
        return compare(*this, other) != 0;
    }

    void copyTextLocally() {
        str.setTo((const char16_t*) text, glyphCount);
        text = str.string();
        if (positions != nullptr) {
            positionsCopy.clear();
            positionsCopy.appendArray(positions, glyphCount * 2);
            positions = positionsCopy.array();
        }
    }

    uint32_t glyphCount;
    float radius;
    float textSize;
    SkTypeface* typeface;
    uint32_t flags;
    float italicStyle;
    float scaleX;
    const char16_t* text;
    const float* positions;

    // Not directly used to compute the cache key
    String16 str;
    Vector<float> positionsCopy;

}; // struct ShadowText

// Caching support

inline int strictly_order_type(const ShadowText& lhs, const ShadowText& rhs) {
    return ShadowText::compare(lhs, rhs) < 0;
}

inline int compare_type(const ShadowText& lhs, const ShadowText& rhs) {
    return ShadowText::compare(lhs, rhs);
}

inline hash_t hash_type(const ShadowText& entry) {
    return entry.hash();
}

/**
 * Alpha texture used to represent a shadow.
 */
struct ShadowTexture: public Texture {
    ShadowTexture(Caches& caches): Texture(caches) {
    }

    float left;
    float top;
}; // struct ShadowTexture

class TextDropShadowCache: public OnEntryRemoved<ShadowText, ShadowTexture*> {
public:
    TextDropShadowCache();
    TextDropShadowCache(uint32_t maxByteSize);
    ~TextDropShadowCache();

    /**
     * Used as a callback when an entry is removed from the cache.
     * Do not invoke directly.
     */
    void operator()(ShadowText& text, ShadowTexture*& texture) override;

    ShadowTexture* get(const SkPaint* paint, const char* text,
            int numGlyphs, float radius, const float* positions);

    /**
     * Clears the cache. This causes all textures to be deleted.
     */
    void clear();

    void setFontRenderer(FontRenderer& fontRenderer) {
        mRenderer = &fontRenderer;
    }

    /**
     * Sets the maximum size of the cache in bytes.
     */
    void setMaxSize(uint32_t maxSize);
    /**
     * Returns the maximum size of the cache in bytes.
     */
    uint32_t getMaxSize();
    /**
     * Returns the current size of the cache in bytes.
     */
    uint32_t getSize();

private:
    void init();

    LruCache<ShadowText, ShadowTexture*> mCache;

    uint32_t mSize;
    uint32_t mMaxSize;
    FontRenderer* mRenderer;
    bool mDebugEnabled;
}; // class TextDropShadowCache

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_TEXT_DROP_SHADOW_CACHE_H

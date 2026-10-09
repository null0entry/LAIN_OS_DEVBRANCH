#pragma once

// The lifecycle harness exercises the production JNI entry points on a host.
// This small adapter supplies only the JNI operations used by that source.
#include <algorithm>
#include <cstdint>
#include <string>
#include <vector>

#define JNIEXPORT
#define JNICALL
#define JNI_TRUE 1
#define JNI_FALSE 0

using jboolean = unsigned char;
using jbyte = int8_t;
using jint = int32_t;
using jlong = int64_t;
using jsize = int32_t;
using jobject = void *;
using jstring = std::string *;
using jbyteArray = std::vector<jbyte> *;

class JNIEnv {
public:
    jbyteArray NewByteArray(jsize size) {
        return new std::vector<jbyte>(static_cast<size_t>(size));
    }

    void SetByteArrayRegion(jbyteArray array, jsize start, jsize size, const jbyte * bytes) {
        if (!valid_region(array, start, size)) return;
        std::copy(bytes, bytes + size, array->begin() + start);
    }

    void GetByteArrayRegion(jbyteArray array, jsize start, jsize size, jbyte * bytes) {
        if (!valid_region(array, start, size)) return;
        std::copy(array->begin() + start, array->begin() + start + size, bytes);
    }

    jsize GetArrayLength(jbyteArray array) {
        return static_cast<jsize>(array->size());
    }

    const char * GetStringUTFChars(jstring string, jboolean *) {
        return string->c_str();
    }

    void ReleaseStringUTFChars(jstring, const char *) {}
    jboolean ExceptionCheck() const { return exception_ ? JNI_TRUE : JNI_FALSE; }

private:
    bool exception_ = false;

    bool valid_region(jbyteArray array, jsize start, jsize size) {
        if (array == nullptr || start < 0 || size < 0 ||
                static_cast<size_t>(start) + static_cast<size_t>(size) > array->size()) {
            exception_ = true;
            return false;
        }
        return true;
    }
};

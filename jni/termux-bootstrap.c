#include <jni.h>
#include <sys/stat.h>

extern jbyte blob[];
extern int blob_size;

//JNIEXPORT jbyteArray JNICALL Java_com_termux_app_TermuxInstaller_getZip(JNIEnv *env, __attribute__((__unused__)) jobject This)

JNIEXPORT jbyteArray JNICALL Java_com_github_jigokumaster_termux4all_TermuxInstaller_getZip(JNIEnv *env, __attribute__((__unused__)) jobject This)
{
    jbyteArray ret = (*env)->NewByteArray(env, blob_size);
    (*env)->SetByteArrayRegion(env, ret, 0, blob_size, blob);
    return ret;
}


JNIEXPORT int JNICALL Java_com_github_jigokumaster_termux4all_TermuxInstaller_chmod(JNIEnv *env, __attribute__((__unused__)) jobject This,jstring j_fp, jint mode)
{

    char const* c_fp = (*env)->GetStringUTFChars(env, j_fp, NULL);
    int ret = chmod(c_fp, mode);
    (*env)->ReleaseStringUTFChars(env, j_fp, c_fp);
    return ret;
}


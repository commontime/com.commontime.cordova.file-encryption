#import "FileEncryption.h"

static NSString *ENCRYPTED_IDENTIFIER = @".encrypted";
static NSString *SERVICE_NAME = @"com.mycompany.myAppServiceName";
static NSString *KEYCHAIN_PASSWORD_KEY = @"encrytionPassword";

static NSString *encryptionPassword;

static NSString *mimeTypeForPath(NSString* path) {
    if(path) {
        path = [path stringByReplacingOccurrencesOfString:ENCRYPTED_IDENTIFIER withString:@""];
        NSString *ret = nil;
        CFStringRef pathExtension = (__bridge_retained CFStringRef)[path pathExtension];
        CFStringRef type = UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, pathExtension, NULL);
        CFRelease(pathExtension);
        if (type != NULL) {
            ret = (__bridge_transfer NSString *)UTTypeCopyPreferredTagWithClass(type, kUTTagClassMIMEType);
            CFRelease(type);
        }
        return ret;
    } else {
        return @"";
    }
}

static NSString *getEncryptedFileName(NSString* originalFileName) {
    NSString *encyptedFileName = [NSString stringWithFormat:@"%@%@", originalFileName, ENCRYPTED_IDENTIFIER];
    return encyptedFileName;
}

static NSString* CTHexStringFromBytes(const uint8_t* bytes, size_t length) {
    if (bytes) {
        if (length == 0) {
            return @"";
        } else {
            NSMutableString* string = [NSMutableString stringWithCapacity: length * 2];
            for (size_t i = 0; i != length; ++i) {
                [string appendFormat: @"%02x", bytes[i]];
            }
            return string;
        }
    } else {
        return nil;
    }
}

static NSMutableDictionary* newSearchDictionary(NSString* identifier) {
    NSMutableDictionary *searchDictionary = [[NSMutableDictionary alloc] init];
    [searchDictionary setObject:(id)kSecClassGenericPassword forKey:(id)kSecClass];
    NSData *encodedIdentifier = [identifier dataUsingEncoding:NSUTF8StringEncoding];
    [searchDictionary setObject:encodedIdentifier forKey:(id)kSecAttrGeneric];
    [searchDictionary setObject:encodedIdentifier forKey:(id)kSecAttrAccount];
    [searchDictionary setObject:SERVICE_NAME forKey:(id)kSecAttrService];
    return searchDictionary;
}

static NSData* searchKeychainCopyMatching(NSString* identifier) {
    NSMutableDictionary *searchDictionary = newSearchDictionary(identifier);
    [searchDictionary setObject:(id)kSecMatchLimitOne forKey:(id)kSecMatchLimit];
    [searchDictionary setObject:(id)kCFBooleanTrue forKey:(id)kSecReturnData];
    NSData *result = nil;
    SecItemCopyMatching((CFDictionaryRef)searchDictionary, (CFTypeRef)&result);
    return result;
}

static BOOL createKeychainValue(NSString* password, NSString* identifier) {
    NSMutableDictionary *dictionary = newSearchDictionary(identifier);
    NSData *passwordData = [password dataUsingEncoding:NSUTF8StringEncoding];
    [dictionary setObject:passwordData forKey:(id)kSecValueData];
    OSStatus status = SecItemAdd((CFDictionaryRef)dictionary, NULL);
    if (status == errSecSuccess) {
        return YES;
    }
    return NO;
}

@implementation FileEncryption

- (void) pluginInitialize
{
    [NSURLProtocol registerClass:[UrlRemapURLProtocol class]];
    
    NSData* storedPassword = searchKeychainCopyMatching(KEYCHAIN_PASSWORD_KEY);
    
    if (storedPassword == nil)
    {
        int numBits = 128;
        size_t numBytes = (size_t) numBits / 8;
        uint8_t buffer[numBytes];
        
        int result = SecRandomCopyBytes(kSecRandomDefault, numBytes, buffer);
        
        if (result == 0) {
            encryptionPassword = CTHexStringFromBytes(buffer, numBytes);
            createKeychainValue(encryptionPassword, KEYCHAIN_PASSWORD_KEY);
        }
    } else {
        NSString* newStr = [[NSString alloc] initWithData:storedPassword encoding:NSUTF8StringEncoding];
        encryptionPassword = newStr;
    }
}

- (void)encrypt:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult *pluginResult = nil;
    
    NSString *filePath = [command.arguments objectAtIndex:0];
    
    NSString *mimeType = mimeTypeForPath(filePath);
    
    if ([mimeType isEqualToString:@"image/jpeg"] || [mimeType isEqualToString:@"image/jpg"] || [mimeType isEqualToString:@"image/png"])
    {
        NSString *path = [self crypto:@"encrypt" command:command];
        
        if (path != nil) {
            pluginResult =
            [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                              messageAsString:path];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
        }
    }
    else {
        pluginResult =
        [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                          messageAsString:filePath];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult
                                callbackId:command.callbackId];
}

- (void)decrypt:(CDVInvokedUrlCommand *)command {
    
    CDVPluginResult *pluginResult = nil;
    
    NSString *filePath = [command.arguments objectAtIndex:0];
    
    NSString *mimeType = mimeTypeForPath(filePath);
    
    if ([mimeType isEqualToString:@"image/jpeg"] || [mimeType isEqualToString:@"image/jpg"] || [mimeType isEqualToString:@"image/png"])
    {
        NSString *path = [self crypto:@"decrypt" command:command];
        
        if (path != nil) {
            pluginResult =
            [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                              messageAsString:path];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
        }
    }
    else
    {
        pluginResult =
        [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                          messageAsString:filePath];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult
                                callbackId:command.callbackId];
}

- (NSString*)crypto:(NSString *)action command:(CDVInvokedUrlCommand *)command {
    
    NSData *data = nil;
    NSString *filePath = [command.arguments objectAtIndex:0];
    NSString *fileName = [filePath lastPathComponent];
    NSFileManager *fileManager = [NSFileManager defaultManager];
    NSString *documentsPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) firstObject];
    NSString *path = [documentsPath stringByAppendingPathComponent:fileName];
    BOOL fileExists = [fileManager fileExistsAtPath:path];
    NSString *encryptedFilePath = [documentsPath stringByAppendingPathComponent:getEncryptedFileName(fileName)];
    BOOL encryptedFileExists = [fileManager fileExistsAtPath:encryptedFilePath];
    
    // if path and password args exist
    if (path != nil && [path length] > 0) {

        if (fileExists || encryptedFileExists) {
            
            NSError *error;
            if ([action isEqualToString:@"encrypt"]) {
                
                NSData *fileData = [NSData dataWithContentsOfFile:path];
                
                data = [RNEncryptor encryptData:fileData
                                   withSettings:kRNCryptorAES256Settings
                                       password:encryptionPassword
                                          error:&error];
                
                [fileManager createFileAtPath:encryptedFilePath contents:data attributes:nil];
                [fileManager removeItemAtPath:path error:nil];
                
                return encryptedFilePath;
                
            } else if ([action isEqualToString:@"decrypt"]) {
                
                NSData *encryptedFileData = [NSData dataWithContentsOfFile:encryptedFilePath];
                
                data = [RNDecryptor decryptData:encryptedFileData
                                   withPassword:encryptionPassword
                                          error:&error];
                
                [fileManager createFileAtPath:path contents:data attributes:nil];
                [fileManager removeItemAtPath:encryptedFilePath error:nil];
                
                return path;
            }
            
        } else {
            path = nil;
        }
    }
    
    return nil;
}

@end

@implementation UrlRemapURLProtocol

+ (BOOL)canInitWithRequest:(NSURLRequest*)request {
    NSString *url = request.URL.absoluteString;
    NSString *fileName = url.lastPathComponent;
    NSString *scheme = request.URL.scheme;
    NSString *mimeType = mimeTypeForPath(url);
    if ([scheme isEqualToString:@"file"] == NO) {
        return NO;
    }
    if ([fileName containsString:ENCRYPTED_IDENTIFIER] && ([mimeType isEqualToString:@"image/jpeg"] || [mimeType isEqualToString:@"image/jpg"] || [mimeType isEqualToString:@"image/png"])) {
        return YES;
    }
    return NO;
}

+ (NSURLRequest*)canonicalRequestForRequest:(NSURLRequest*)request {
    return request;
}

- (void)startLoading {
    NSString *filePath = [self.request.URL.absoluteString stringByReplacingOccurrencesOfString:@"file://" withString:@""];
    NSData *fileData = [NSData dataWithContentsOfFile:filePath];
    NSData *decryptedData = [RNDecryptor decryptData:fileData withPassword:encryptionPassword error:nil];
        
    NSURL* dataURL = [NSURL URLWithDataRepresentation:decryptedData relativeToURL:self.request.URL];
    NSURLResponse *response = [[NSURLResponse alloc] initWithURL:dataURL MIMEType:mimeTypeForPath(filePath) expectedContentLength:decryptedData.length textEncodingName:nil];
    
    [self.client URLProtocol:self didReceiveResponse:response cacheStoragePolicy:NSURLCacheStorageNotAllowed];
    [self.client URLProtocol:self didLoadData:decryptedData];
    [self.client URLProtocolDidFinishLoading:self];
}

- (void)stopLoading {
}

@end
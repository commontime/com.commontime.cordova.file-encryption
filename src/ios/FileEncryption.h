#import <Cordova/CDVPlugin.h>
#import <MobileCoreServices/MobileCoreServices.h>
#import "RNEncryptor.h"
#import "RNDecryptor.h"

@interface FileEncryption : CDVPlugin

- (void)encrypt:(CDVInvokedUrlCommand*)command;
- (void)decrypt:(CDVInvokedUrlCommand*)command;

@end

@interface UrlRemapURLProtocol : NSURLProtocol
@end
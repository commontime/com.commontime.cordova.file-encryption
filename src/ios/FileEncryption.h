#import <Cordova/CDVPlugin.h>
#import <MobileCoreServices/MobileCoreServices.h>
#import "RNDecryptor.h"
#import "RNEncryptor.h"

@interface FileEncryption : CDVPlugin

- (void)encrypt:(CDVInvokedUrlCommand*)command;
- (void)decrypt:(CDVInvokedUrlCommand*)command;

@end

@interface UrlRemapURLProtocol : NSURLProtocol
@end
#import <Cordova/CDVPlugin.h>

@interface FileEncryption : CDVPlugin

- (void)encrypt:(CDVInvokedUrlCommand*)command;
- (void)decrypt:(CDVInvokedUrlCommand*)command;

@end
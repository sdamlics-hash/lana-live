import io
import json
import unittest
import urllib.error
from email.message import Message
from staging.check_connection import check_connection, ConnectionCheckError, NoRedirect

TOKEN='test-only-'+('x'*40)
URL='https://lana.example'
class Response(io.BytesIO):
    def __init__(self,data,url=URL+'/v1/status',ctype='application/json'):
        super().__init__(data);self.status=200;self.url=url;self.headers=Message();self.headers['Content-Type']=ctype
    def geturl(self):return self.url
class Client:
    def __init__(self,items):self.items=list(items);self.calls=[]
    def open(self,req,timeout):
        self.calls.append(req)
        item=self.items.pop(0)
        if isinstance(item,Exception):raise item
        return item

def error(code):return urllib.error.HTTPError(URL,code,'private body',{},None)
def valid():return Response(json.dumps({'ok':True,'mode':'offline-prototype','allowed':['heartbeat','trade_report']}).encode())
class StagingConnectionTests(unittest.TestCase):
    def test_two_read_only_requests_no_provider_call(self):
        client=Client([error(401),valid()]);result=check_connection(URL,TOKEN,client)
        self.assertTrue(result['https_verified']);self.assertEqual(result['paid_api_calls'],0)
        self.assertFalse(result['live_model_tested']);self.assertFalse(result['memory_persistence_tested'])
        self.assertNotIn('Authorization',client.calls[0].headers)
        self.assertEqual(client.calls[1].get_header('Authorization'),'Bearer '+TOKEN)
        self.assertTrue(all(x.get_method()=='GET' and x.full_url==URL+'/v1/status' for x in client.calls))
    def test_public_status_not_accepted(self):
        with self.assertRaises(ConnectionCheckError):check_connection(URL,TOKEN,Client([valid()]))
    def test_bad_token_does_not_echo_secret(self):
        with self.assertRaises(ConnectionCheckError) as ctx:check_connection(URL,TOKEN,Client([error(401),error(403)]))
        self.assertNotIn(TOKEN,str(ctx.exception))
    def test_redirects_disabled(self):
        self.assertIsNone(NoRedirect().redirect_request(None,None,302,'',{},'https://evil.test'))
        with self.assertRaises(ConnectionCheckError):check_connection(URL,TOKEN,Client([error(302)]))
    def test_invalid_urls_before_network(self):
        for url in ['http://lana.example','https://user:pass@lana.example','https://lana.example?key=x','https://lana.example#x','https://lana.example:bad','https://lana.example/ x']:
            with self.subTest(url=url),self.assertRaises(ConnectionCheckError):check_connection(url,TOKEN,Client([]))
    def test_tls_failure_redacted(self):
        with self.assertRaises(ConnectionCheckError) as ctx:check_connection(URL,TOKEN,Client([OSError(TOKEN)]))
        self.assertNotIn(TOKEN,str(ctx.exception))
    def test_html_oversized_and_wrong_service_rejected(self):
        for response in [Response(b'<html>',ctype='text/html'),Response(b'x'*8193),Response(b'{}'),Response(b'{'),Response(b'{"ok":true,"mode":"offline-prototype","allowed":[]}')]:
            with self.subTest(),self.assertRaises(ConnectionCheckError):check_connection(URL,TOKEN,Client([error(401),response]))

class RealTLSConnectionTests(unittest.TestCase):
    def test_local_tls_requires_trusted_certificate_and_token(self):
        import shutil
        import ssl
        import subprocess
        import tempfile
        import threading
        import urllib.request
        from pathlib import Path
        from lana_core.api import create_server
        from lana_core.engine import TaskQueue
        if not shutil.which('openssl'):self.skipTest('openssl not installed')
        with tempfile.TemporaryDirectory() as folder:
            cert=str(Path(folder)/'cert.pem');key=str(Path(folder)/'key.pem')
            subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',key,'-out',cert,'-days','1','-subj','/CN=127.0.0.1','-addext','subjectAltName=IP:127.0.0.1'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            server=create_server(TaskQueue(str(Path(folder)/'tasks.db')),TOKEN,port=0)
            tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);tls.load_cert_chain(cert,key)
            server.socket=tls.wrap_socket(server.socket,server_side=True)
            thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            url='https://127.0.0.1:'+str(server.server_address[1])
            try:
                with self.assertRaises(ConnectionCheckError):check_connection(url,TOKEN)
                trusted=urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=cert)),NoRedirect())
                self.assertTrue(check_connection(url,TOKEN,trusted)['authentication_verified'])
                with self.assertRaises(ConnectionCheckError):check_connection(url,'wrong-token-'+'z'*40,trusted)
            finally:
                server.shutdown();server.server_close();thread.join(timeout=3)

import contextlib
import http.server
import io
import json
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.probe_existing_lana import probe,read_token
TOKEN='test-only-'+'x'*40
class ProbeTests(unittest.TestCase):
    def setUp(self):
        self.calls=[];self.status=200;self.response={'reply':'PRIVATE_REPLY_MUST_NOT_PRINT'}
        owner=self
        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def do_POST(self):
                owner.calls.append((self.path,self.headers.get('Authorization'),json.loads(self.rfile.read(int(self.headers['Content-Length'])))))
                self.send_response(owner.status)
                if owner.status==302:self.send_header('Location','http://127.0.0.1:'+str(self.server.server_port)+'/redirected')
                self.end_headers();self.wfile.write(json.dumps(owner.response).encode())
        self.server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join(3)
    def test_exactly_one_observed_contract_request_and_no_reply_or_key_output(self):
        out=io.StringIO()
        with contextlib.redirect_stdout(out):result=probe(TOKEN,self.server.server_port)
        self.assertEqual(len(self.calls),1)
        path,auth,body=self.calls[0]
        self.assertEqual(path,'/v1/lana/chat');self.assertEqual(auth,'Bearer '+TOKEN)
        self.assertEqual(set(body),{'message','history'});self.assertEqual(body['history'],[])
        self.assertTrue(result['dialogue_verified']);self.assertFalse(result['openai_provider_verified'])
        for secret in (TOKEN,'PRIVATE_REPLY_MUST_NOT_PRINT'):
            self.assertNotIn(secret,json.dumps(result)+out.getvalue())
    def test_redirect_is_not_followed_or_retried(self):
        self.status=302
        result=probe(TOKEN,self.server.server_port)
        self.assertEqual(result['http_status'],302);self.assertEqual(len(self.calls),1)
        self.assertFalse(result['dialogue_verified'])
    def test_auth_failure_is_not_connected_and_error_body_is_not_echoed(self):
        self.status=401;self.response={'error':TOKEN}
        result=probe(TOKEN,self.server.server_port)
        self.assertFalse(result['dialogue_verified']);self.assertNotIn(TOKEN,str(result));self.assertEqual(len(self.calls),1)
    def test_wrong_reply_type_is_not_connected(self):
        self.response={'reply':True}
        self.assertFalse(probe(TOKEN,self.server.server_port)['dialogue_verified'])
    def test_env_parser_never_executes_shell_and_only_returns_requested_token(self):
        with tempfile.TemporaryDirectory() as d,patch.dict('os.environ',{},clear=True):
            path=Path(d)/'.env';path.write_text('OPENAI_API_KEY=do-not-use\nexport LANA_BACKEND_TOKEN="'+TOKEN+'" # note\n')
            self.assertEqual(read_token(path),TOKEN)
            path.write_text('LANA_BACKEND_TOKEN="$(must-not-execute)"\n')
            self.assertEqual(read_token(path),'$(must-not-execute)')

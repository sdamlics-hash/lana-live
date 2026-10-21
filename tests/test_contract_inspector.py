import unittest
from tools.inspect_lana_contract import inspect_source
class ContractInspectorTests(unittest.TestCase):
    def test_only_actual_decorated_routes_and_referenced_field_names(self):
        source='''
SECRET="never-print-me"
class ChatRequest(BaseModel):
    message: str
    token: str = "secret-default"
class Other(BaseModel):
    unrelated: str
@app.post("/v1/lana/chat")
async def chat(req: ChatRequest):
    secret = os.environ.get("OPENAI_API_KEY")
    return {"answer": SECRET, "conversation_id": "sensitive-id"}
@app.get("/admin/private")
def hidden():
    return {"private": "x"}
'''
        result=inspect_source(source)
        self.assertEqual(result['models'],{'ChatRequest':['message','token']})
        self.assertEqual(len(result['routes']),1)
        self.assertEqual(result['routes'][0]['literal_response_keys'],['answer','conversation_id'])
        for secret in ('never-print-me','secret-default','sensitive-id','OPENAI_API_KEY','unrelated'):
            self.assertNotIn(secret,str(result))
    def test_does_not_execute_source(self):
        self.assertEqual(inspect_source('raise RuntimeError("must not execute")')['routes'],[])

"""无需真实模型，验证LSTM线程归属、融合列顺序及执行器复用。"""
import importlib.util
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
import numpy as np


class ExecutorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        script = Path(__file__).parents[2] / 'main/resources/python/predict.py'
        sys.path.insert(0, str(script.parent))
        spec = importlib.util.spec_from_file_location('subject', script)
        self.m = importlib.util.module_from_spec(spec)
        with patch.object(sys, 'argv', ['predict.py', '--model-dir', self.temp.name]):
            spec.loader.exec_module(self.m)

    def tearDown(self):
        self.m.MODEL_EXECUTOR.shutdown(wait=True)
        self.m.LSTM_EXECUTOR.shutdown(wait=True)
        self.temp.cleanup()
        sys.path.pop(0)

    def test_lstm_load_and_every_prediction_share_one_thread(self):
        ids = []
        class Scaler:
            def transform(self, x): return x
        class Model:
            def predict(self, x, verbose):
                ids.append(threading.get_ident())
                return np.array([.3])
        def load():
            ids.append(threading.get_ident())
            self.m.loaded_models.update(lstm_model=Model(), lstm_scaler=Scaler(), lstm_n_features=6)
        with patch.object(self.m, 'load_lstm_model', load), patch.object(self.m, 'load_traditional_ml_models'), \
                patch.object(self.m, 'load_transformer_model'), patch.object(self.m, 'load_markov_model'), \
                patch.object(self.m, 'load_fusion_models'):
            self.m.load_all_models()
        for _ in range(20):
            assert self.m.model_proba('lstm_model', np.zeros((1, 6)), np.zeros((1, 6)), 0) == .3
        self.assertEqual(1, len(set(ids)))
        self.assertNotEqual(threading.get_ident(), ids[0])

    def test_fusion_keeps_declared_feature_order_and_worker_set_is_bounded(self):
        seen = []
        class Fusion:
            def predict_proba(self, x):
                seen.append(x.tolist())
                return np.array([[.7, .3]])
        self.m.loaded_models.update(second_fusion_model=Fusion(), second_fusion_meta_features=['xgb', 'rf', 'markov'])
        ids = set()
        def proba(key, *args):
            ids.add(threading.get_ident())
            return {'xgb_is_positive': .1, 'rf_is_positive': .2, 'markov_model': .3}[key]
        with patch.object(self.m, 'model_proba', proba):
            for _ in range(40):
                probability, reason = self.m.fuse_prediction(None, None, None)
                self.assertEqual(.3, probability)
        self.assertTrue(all(row == [[.1, .2, .3]] for row in seen))
        self.assertLessEqual(len(ids), 7)


if __name__ == '__main__':
    unittest.main()

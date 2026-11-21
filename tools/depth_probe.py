import sys, hashlib, numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter
model, img_path, outdir = sys.argv[1:4]
print("sha256", hashlib.sha256(open(model,'rb').read()).hexdigest())
it = Interpreter(model_path=model, num_threads=4)
it.allocate_tensors()
inp, out = it.get_input_details(), it.get_output_details()
print("INPUT", inp); print("OUTPUT", out)
shape = inp[0]['shape']; nchw = shape[1] == 3
S = int(shape[2] if nchw else shape[1])
im = Image.open(img_path).convert("RGB").resize((S, S), Image.BILINEAR)
a = np.asarray(im, dtype=np.float32) / 255.0
mean = np.array([0.485, 0.456, 0.406], np.float32); std = np.array([0.229, 0.224, 0.225], np.float32)
for tag, x in (("raw01", a), ("imagenet", (a - mean) / std)):
    x = np.transpose(x, (2, 0, 1)) if nchw else x
    it.set_tensor(inp[0]['index'], x[None].astype(inp[0]['dtype']))
    it.invoke()
    d = it.get_tensor(out[0]['index']).astype(np.float32)
    print(tag, "out shape", d.shape, "min", d.min(), "max", d.max(), "mean", d.mean())
    d = np.squeeze(d)
    n = (d - d.min()) / max(d.max() - d.min(), 1e-6)
    Image.fromarray((n * 255).astype(np.uint8)).save(f"{outdir}/depth_{tag}.png")
    g = n.reshape(12, n.shape[0] // 12, 12, n.shape[1] // 12).mean(axis=(1, 3))
    print("GRID", tag)
    for row in g: print("GRID " + " ".join(str(int(v * 9.99)) for v in row))
im.save(f"{outdir}/input.png")

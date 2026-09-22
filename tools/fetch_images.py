# -*- coding: utf-8 -*-
"""从 Wikimedia Commons 为 51 个景点下载真实照片（免费版权）。
用法: python fetch_images.py
输出: frontend/public/img/scenic/a{ID}.jpg  +  tools/image_result.json
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

UA = {"User-Agent": "TourismSiteSeed/1.0 (educational project; contact: local)"}
PROXY = urllib.request.ProxyHandler(
    {"http": "http://127.0.0.1:7897", "https": "http://127.0.0.1:7897"}
)
OPENER = urllib.request.build_opener(PROXY)

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "frontend", "public", "img", "scenic")
OUT_DIR = os.path.abspath(OUT_DIR)
os.makedirs(OUT_DIR, exist_ok=True)

# id -> (中文名, [搜索词候选])  英文优先，中文兜底
ATTRACTONS = {
    1:  ("故宫博物院",   ["Forbidden City Beijing", "故宫"]),
    2:  ("八达岭长城",   ["Badaling Great Wall", "八达岭长城"]),
    3:  ("颐和园",       ["Summer Palace Beijing", "颐和园"]),
    4:  ("天坛公园",     ["Temple of Heaven", "天坛"]),
    5:  ("古观象台",     ["Beijing Ancient Observatory", "北京古观象台"]),
    6:  ("北海公园",     ["Beihai Park Beijing", "北海公园 北京"]),
    7:  ("东方明珠广播电视塔", ["Oriental Pearl Tower", "东方明珠"]),
    8:  ("上海野生动物园", ["Shanghai Wild Animal Park", "上海野生动物园"]),
    9:  ("杭州西湖风景名胜区", ["West Lake Hangzhou", "西湖 杭州"]),
    10: ("西溪国家湿地公园", ["Xixi Wetland Park Hangzhou", "西溪湿地"]),
    11: ("普陀山风景名胜区", ["Mount Putuo", "普陀山"]),
    12: ("乌镇古镇旅游区", ["Wuzhen", "乌镇"]),
    13: ("象山影视城",   ["Xiangshan Movie City", "象山影视城"]),
    14: ("黄山风景区",   ["Huangshan mountain", "黄山"]),
    15: ("九华山风景区", ["Mount Jiuhua", "九华山"]),
    16: ("秦始皇兵马俑博物馆", ["Terracotta Army", "兵马俑"]),
    17: ("华山风景名胜区", ["Mount Hua", "华山"]),
    18: ("大雁塔·大唐芙蓉园景区", ["Giant Wild Goose Pagoda", "大雁塔"]),
    19: ("西安城墙景区", ["City wall of Xi'an", "西安城墙"]),
    20: ("九寨沟风景名胜区", ["Jiuzhaigou", "九寨沟"]),
    21: ("峨眉山风景名胜区", ["Mount Emei", "峨眉山"]),
    22: ("成都大熊猫繁育研究基地", ["Chengdu Panda Base", "成都大熊猫繁育研究基地"]),
    23: ("乐山大佛景区", ["Leshan Giant Buddha", "乐山大佛"]),
    24: ("桂林漓江风景名胜区", ["Li River Guilin", "漓江"]),
    25: ("青秀山旅游区", ["青秀山 龙象塔", "Qingxiu Mountain pagoda Nanning"]),
    26: ("丽江古城景区", ["Old Town of Lijiang", "丽江古城"]),
    27: ("石林风景区",   ["Shilin Stone Forest", "石林 云南"]),
    28: ("大理崇圣寺三塔文化旅游区", ["Three Pagodas Chongsheng Temple Dali", "崇圣寺三塔"]),
    29: ("中山陵园风景区", ["中山陵 博爱坊", "Sun Yat-sen Mausoleum staircase", "中山陵天下为公"]),
    30: ("苏州园林（拙政园）", ["Humble Administrator's Garden", "拙政园"]),
    31: ("木渎古镇",     ["Mudu town Suzhou", "木渎古镇"]),
    32: ("瘦西湖风景名胜区", ["Slender West Lake", "瘦西湖"]),
    33: ("张家界武陵源风景名胜区", ["Wulingyuan Zhangjiajie", "张家界"]),
    34: ("岳阳楼·君山岛景区", ["Yueyang Tower", "岳阳楼"]),
    35: ("泰山风景名胜区", ["Mount Tai", "泰山"]),
    36: ("济南天下第一泉风景区", ["趵突泉 泉水", "Baotu Spring gushing"]),
    37: ("红叶谷生态文化旅游区", ["Red Leaf Valley Jinan", "济南红叶谷"]),
    38: ("青岛崂山风景名胜区", ["Mount Lao Qingdao scenery", "崂山 风景", "Laoshan mountain coast"]),
    39: ("青岛海底世界", ["Qingdao Underwater World", "青岛海底世界"]),
    40: ("周村古商城",   ["周村古商城 大街", "Zhoucun ancient city", "周村区"]),
    41: ("鼓浪屿风景名胜区", ["Gulangyu", "鼓浪屿"]),
    42: ("武夷山风景名胜区", ["Wuyi Mountains", "武夷山"]),
    43: ("嘉峪关文物景区", ["Jiayuguan Fort", "嘉峪关"]),
    44: ("敦煌鸣沙山月牙泉景区", ["Crescent Lake Dunhuang", "月牙泉"]),
    45: ("黄鹤楼公园",   ["黄鹤楼 建筑", "Yellow Crane Tower 2021"]),
    46: ("三峡大坝旅游区", ["Three Gorges Dam", "三峡大坝"]),
    47: ("武当山风景区", ["Wudang Mountains", "武当山"]),
    48: ("南山文化旅游区", ["Nanshan Temple Sanya Guanyin", "南山海上观音"]),
    49: ("亚龙湾热带天堂森林公园", ["Yalong Bay", "亚龙湾"]),
    50: ("清明上河园",   ["Millennium City Park Kaifeng", "清明上河园"]),
    51: ("龙门石窟景区", ["Longmen Grottoes", "龙门石窟"]),
    52: ("豫园",         ["Yuyuan Garden rockery pavilion", "豫园 湖心亭", "豫园园林"]),
    53: ("西塘古镇",     ["Xitang bridge night", "西塘 烟雨长廊"]),
    54: ("天一阁博物院", ["Tianyi Pavilion", "天一阁"]),
    55: ("宏村景区",     ["Hongcun", "宏村"]),
    56: ("华清宫景区",   ["Huaqing Pool", "华清池"]),
    57: ("都江堰景区",   ["Dujiangyan irrigation system", "都江堰 水利工程"]),
    58: ("宽窄巷子",     ["宽窄巷子 街道", "Kuanzhai Xiangzi street"]),
    59: ("龙脊梯田景区", ["Longsheng rice terraces", "龙脊梯田"]),
    60: ("玉龙雪山景区", ["玉龙雪山 雪峰", "玉龙雪山 冰川公园", "Jade Dragon Snow Mountain peak"]),
    61: ("滇池海埂公园", ["Dianchi Lake Kunming", "滇池"]),
    62: ("夫子庙秦淮风光带", ["Confucius Temple Nanjing Qinhuai", "夫子庙"]),
    63: ("周庄古镇景区", ["Zhouzhuang", "周庄"]),
    64: ("个园",         ["Ge Garden Yangzhou", "个园 扬州"]),
    65: ("天门山国家森林公园", ["Tianmen Mountain", "天门山"]),
    66: ("千佛山风景名胜区", ["Qianfo Mountain Jinan", "千佛山 济南"]),
    67: ("栈桥",         ["Zhanqiao Pier Qingdao", "青岛栈桥"]),
    68: ("南普陀寺",     ["South Putuo Temple Xiamen", "南普陀寺"]),
    69: ("厦门园林植物园", ["Xiamen Botanical Garden", "厦门园林植物园"]),
    70: ("武汉东湖风景名胜区", ["East Lake Wuhan", "武汉东湖"]),
    71: ("三峡人家风景区", ["Xiling Gorge Yangtze", "三峡人家 宜昌"]),
    72: ("蜈支洲岛旅游区", ["蜈支洲岛 海滩", "Wuzhizhou Island beach"]),
    73: ("天涯海角游览区", ["Tianya Haijiao", "天涯海角"]),
    74: ("龙亭公园",     ["Longting Park Kaifeng", "龙亭 开封"]),
    75: ("白马寺",       ["White Horse Temple", "白马寺"]),
}

BAD_NAME_WORDS = ("map", "location", "svg", "diagram", "logo", "flag", "plan", "chart", "sign")


def api_call(params):
    qs = urllib.parse.urlencode(params)
    url = "https://commons.wikimedia.org/w/api.php?" + qs
    req = urllib.request.Request(url, headers=UA)
    with OPENER.open(req, timeout=25) as r:
        return json.loads(r.read().decode("utf-8"))


def search_image(query):
    """返回 (缩略图url, 文件名) 或 None"""
    data = api_call({
        "action": "query", "format": "json",
        "generator": "search", "gsrsearch": query,
        "gsrnamespace": "6", "gsrlimit": "8",
        "prop": "imageinfo", "iiprop": "url|size|mime",
        "iiurlwidth": "1000",
    })
    pages = (data.get("query") or {}).get("pages") or {}
    # 按搜索排序（index 越小越相关）
    for _, page in sorted(pages.items(), key=lambda kv: kv[1].get("index", 99)):
        title = page.get("title", "").lower()
        if any(w in title for w in BAD_NAME_WORDS):
            continue
        infos = page.get("imageinfo") or []
        if not infos:
            continue
        info = infos[0]
        mime = info.get("mime", "")
        if mime not in ("image/jpeg", "image/png"):
            continue
        if info.get("width", 0) < 640:
            continue
        thumb = info.get("thumburl") or info.get("url")
        if thumb:
            return thumb, page.get("title")
    return None


def download(url, path):
    req = urllib.request.Request(url, headers=UA)
    with OPENER.open(req, timeout=40) as r, open(path, "wb") as f:
        f.write(r.read())


def main():
    results = {}
    for aid, (name, queries) in sorted(ATTRACTONS.items()):
        dest = os.path.join(OUT_DIR, f"a{aid}.jpg")
        if os.path.exists(dest) and os.path.getsize(dest) > 20000:
            results[aid] = {"ok": True, "file": f"/img/scenic/a{aid}.jpg", "note": "cached"}
            print(f"[{aid:02d}] {name}: cached, skip")
            continue
        got = None
        for q in queries:
            hit = None
            for attempt in range(3):
                try:
                    hit = search_image(q)
                    break
                except Exception as e:
                    if "429" in str(e) and attempt < 2:
                        time.sleep(8)
                        continue
                    print(f"[{aid:02d}] {name}: search '{q}' error {e}")
                    break
            if hit:
                got = (hit[0], f"{hit[1]} (via '{q}')")
                break
            time.sleep(1.5)  # 降低请求频率，避免 429 限流
        if not got:
            results[aid] = {"ok": False, "note": "no image found"}
            print(f"[{aid:02d}] {name}: !! NOT FOUND")
            continue
        try:
            download(got[0], dest)
            size_kb = os.path.getsize(dest) // 1024
            results[aid] = {"ok": True, "file": f"/img/scenic/a{aid}.jpg", "source": got[1]}
            print(f"[{aid:02d}] {name}: ok {size_kb}KB <- {got[1]}")
        except Exception as e:
            results[aid] = {"ok": False, "note": f"download failed: {e}"}
            print(f"[{aid:02d}] {name}: download error {e}")

    ok = sum(1 for r in results.values() if r["ok"])
    print(f"\n=== {ok}/{len(ATTRACTONS)} downloaded ===")
    with open(os.path.join(os.path.dirname(__file__), "image_result.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()

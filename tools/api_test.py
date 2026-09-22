# -*- coding: utf-8 -*-
"""旅游景点网站 API 全功能验证脚本"""
import json
import time
import urllib.request
import urllib.error
import sys

BASE = 'http://localhost/api'
PASSED = []
FAILED = []
# 每次运行生成唯一用户名，保证脚本可重复执行
TEST_USER = 'apitester' + str(int(time.time()))[-8:]


def call(method, path, body=None, token=None, expect_code=0):
    url = BASE + path
    req = urllib.request.Request(url, method=method)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    data = json.dumps(body).encode('utf-8') if body is not None else None
    try:
        resp = urllib.request.urlopen(req, data=data, timeout=15)
        return json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode('utf-8'))


def check(name, cond, detail=''):
    if cond:
        PASSED.append(name)
        print(f'  PASS  {name}')
    else:
        FAILED.append((name, detail))
        print(f'  FAIL  {name}  {detail}')


print('==== 一、游客端：注册 ====')
r = call('GET', f'/auth/check-username?username={TEST_USER}')
check('用户名查重-未被注册', r['code'] == 0 and r['data'] is True, str(r))
r = call('POST', '/auth/register', {'username': TEST_USER, 'password': 'test123456',
                                    'confirmPassword': 'test123456', 'nickname': 'API测试用户'})
check('注册成功', r['code'] == 0 and r['data']['username'] == TEST_USER, str(r))
r = call('POST', '/auth/register', {'username': TEST_USER, 'password': 'test123456',
                                    'confirmPassword': 'test123456', 'nickname': 'x'})
check('重复用户名被拦截', r['code'] != 0 and '已被注册' in r['msg'], str(r))
r = call('POST', '/auth/register', {'username': 'apitester002', 'password': 'test123456',
                                    'confirmPassword': 'diff123456', 'nickname': 'x'})
check('两次密码不一致被拦截', r['code'] != 0 and '不一致' in r['msg'], str(r))

print('==== 二、游客端：登录 ====')
r = call('POST', '/auth/login', {'username': TEST_USER, 'password': 'test123456'})
check('注册用户登录成功', r['code'] == 0 and r['data']['role'] == 'USER', str(r))
r = call('POST', '/auth/login', {'username': TEST_USER, 'password': 'wrongpass'})
check('密码错误被拦截', r['code'] != 0 and '错误' in r['msg'], str(r))
r = call('POST', '/auth/login', {'username': 'tourist456', 'password': '123456'})
check('禁用用户登录被拦截', r['code'] == 403 and '禁用' in r['msg'], str(r))
r = call('POST', '/auth/login', {'username': 'tourist123', 'password': '123456'})
check('预置用户登录成功', r['code'] == 0, str(r))
guest_token = r['data']['token']

print('==== 三、游客端：登录墙（未登录不能浏览） ====')
r = call('GET', '/attractions')
check('未登录访问景点列表被拒401', r['code'] == 401, str(r))
r = call('GET', '/attractions/1')
check('未登录访问景点详情被拒401', r['code'] == 401, str(r))
r = call('GET', '/provinces')
check('未登录访问省份列表被拒401', r['code'] == 401, str(r))

print('==== 四、游客端：景点列表/筛选/搜索/排序/分页 ====')
r = call('GET', '/attractions', token=guest_token)
check('登录后列表分页 total=75 / 每页6条', r['data']['total'] == 75 and len(r['data']['list']) == 6, str(r)[:200])
check('列表含省市名称/等级/评分', all(k in r['data']['list'][0] for k in
      ['provinceName', 'cityName', 'level', 'rating', 'views']), str(r['data']['list'][0])[:150])

r = call('GET', '/attractions?keyword=' + urllib.request.quote('长城'), token=guest_token)
check('关键词搜索-长城', r['data']['total'] >= 1 and any('八达岭长城' in a['name'] for a in r['data']['list']), str(r)[:200])

r = call('GET', '/attractions?provinceId=1', token=guest_token)
check('按省筛选-北京', r['data']['total'] == 6, 'total=' + str(r['data']['total']))
r = call('GET', '/attractions?provinceId=3&cityId=3', token=guest_token)
check('按省市筛选-浙江杭州', r['data']['total'] == 2 and all(a['cityName'] == '杭州' for a in r['data']['list']), str(r)[:200])

r = call('GET', '/attractions?level=5A&size=100', token=guest_token)
check('按等级筛选-5A', r['data']['total'] >= 40 and all(a['level'] == '5A' for a in r['data']['list']),
      'total=' + str(r['data']['total']))

r = call('GET', '/attractions?sort=rating&size=10', token=guest_token)
ratings = [float(a['rating']) for a in r['data']['list']]
check('综合评分排序-降序', ratings == sorted(ratings, reverse=True), str(ratings))
r = call('GET', '/attractions?sort=views&size=10', token=guest_token)
views = [a['views'] for a in r['data']['list']]
check('浏览热度排序-降序', views == sorted(views, reverse=True), str(views))

print('==== 五、游客端：景点详情 + 省市联动 ====')
r = call('GET', '/attractions/1', token=guest_token)
views_before = r['data']['views']
check('详情返回完整字段', all(k in r['data'] and r['data'][k] for k in
      ['name', 'description', 'level', 'address', 'openTime', 'ticketPrice']), str(r)[:200])
r2 = call('GET', '/attractions/1', token=guest_token)
check('浏览热度自增+1', r2['data']['views'] == views_before + 1,
      f"{views_before} -> {r2['data']['views']}")
r = call('GET', '/provinces', token=guest_token)
check('省份列表(16省)', r['code'] == 0 and len(r['data']) == 16, str(len(r.get('data', []))))
r = call('GET', '/cities?provinceId=3', token=guest_token)
check('省份联动城市-浙江4城', len(r['data']) == 4 and r['data'][0]['name'] == '杭州', str(r['data']))

print('==== 六、权限控制 ====')
r = call('GET', '/admin/attractions')
check('未登录访问管理接口被拒401', r['code'] == 401, str(r))
r = call('POST', '/auth/login', {'username': 'tourist123', 'password': '123456'})
user_token = r['data']['token']
r = call('GET', '/admin/attractions', token=user_token)
check('普通用户访问管理接口被拒403', r['code'] == 403, str(r))
r = call('POST', '/admin/auth/login', {'username': 'admin', 'password': 'admin123'})
check('管理员登录成功', r['code'] == 0 and r['data']['role'] == 'ADMIN', str(r))
admin_token = r['data']['token']
r = call('POST', '/admin/auth/login', {'username': 'admin', 'password': 'wrong'})
check('管理员密码错误被拦截', r['code'] != 0, str(r))
r_front = call('POST', '/auth/login', {'username': 'admin', 'password': 'admin123'})
check('管理员走前台登录口被拒', r_front['code'] == 403, str(r_front))

print('==== 七、管理端：景点 CRUD ====')
r = call('POST', '/admin/attractions', token=admin_token,
         body={'name': 'API测试景点', 'provinceId': 1, 'cityId': 1, 'level': '4A',
               'image': '/img/scenic2.svg', 'description': '用于验证新增功能的测试景点',
               'address': '测试地址1号', 'openTime': '09:00-17:00', 'ticketPrice': 88, 'rating': 4.5, 'status': 1})
check('新增景点成功', r['code'] == 0, str(r))
new_id = None
r = call('GET', '/admin/attractions?keyword=' + urllib.request.quote('API测试景点'), token=admin_token)
if r['data']['list']:
    new_id = r['data']['list'][0]['id']
check('条件查询能找到新增景点', new_id is not None, str(r)[:200])

r = call('PUT', f'/admin/attractions/{new_id}', token=admin_token,
         body={'name': 'API测试景点(修改)', 'provinceId': 1, 'cityId': 1, 'level': '3A',
               'image': '/img/scenic3.svg', 'description': '修改后的介绍', 'address': '新地址',
               'openTime': '08:00-18:00', 'ticketPrice': 66, 'rating': 4.2, 'status': 1})
check('修改景点成功', r['code'] == 0, str(r))
r = call('GET', f'/attractions/{new_id}', token=guest_token)
check('前台实时可见修改结果', r['data']['name'] == 'API测试景点(修改)' and r['data']['level'] == '3A', str(r)[:150])

r = call('POST', '/admin/attractions', token=admin_token,
         body={'name': '非法等级景点', 'provinceId': 1, 'cityId': 1, 'level': '6A'})
check('非法等级被拦截', r['code'] != 0 and '等级' in r['msg'], str(r))
r = call('POST', '/admin/attractions', token=admin_token,
         body={'name': '省市不匹配', 'provinceId': 1, 'cityId': 3, 'level': '4A'})
check('城市与省份不匹配被拦截', r['code'] != 0 and '不匹配' in r['msg'], str(r))

r = call('DELETE', f'/admin/attractions/{new_id}', token=admin_token)
check('删除景点成功', r['code'] == 0, str(r))
r = call('GET', f'/attractions/{new_id}', token=guest_token)
check('删除后前台不可见', r['code'] != 0, str(r))

print('==== 八、管理端：省市管理 ====')
r = call('POST', '/admin/regions/provinces', token=admin_token, body={'name': '测试省'})
check('新增省份成功', r['code'] == 0, str(r))
r = call('GET', '/provinces', token=guest_token)
test_pid = [p['id'] for p in r['data'] if p['name'] == '测试省'][0]
r = call('POST', '/admin/regions/provinces', token=admin_token, body={'name': '测试省'})
check('重复省份被拦截', r['code'] != 0 and '已存在' in r['msg'], str(r))
r = call('PUT', f'/admin/regions/provinces/{test_pid}', token=admin_token, body={'name': '测试省改'})
check('修改省份成功', r['code'] == 0, str(r))
r = call('POST', '/admin/regions/cities', token=admin_token, body={'name': '测试市', 'provinceId': test_pid})
check('新增城市成功', r['code'] == 0, str(r))
r = call('GET', f'/cities?provinceId={test_pid}', token=guest_token)
test_cid = r['data'][0]['id']
r = call('PUT', f'/admin/regions/cities/{test_cid}', token=admin_token, body={'name': '测试市改'})
check('修改城市成功', r['code'] == 0, str(r))
# 删除被景点引用的省市 → 应提示
r = call('DELETE', '/admin/regions/provinces/1', token=admin_token)
check('删除有景点的省份被拦截并提示', r['code'] != 0 and '景点' in r['msg'], str(r))
r = call('DELETE', '/admin/regions/cities/1', token=admin_token)
check('删除有景点的城市被拦截并提示', r['code'] != 0 and '景点' in r['msg'], str(r))
r = call('DELETE', f'/admin/regions/cities/{test_cid}', token=admin_token)
check('删除无引用城市成功', r['code'] == 0, str(r))
r = call('DELETE', f'/admin/regions/provinces/{test_pid}', token=admin_token)
check('删除无引用省份成功', r['code'] == 0, str(r))

print('==== 九、管理端：用户管理 ====')
r = call('GET', '/admin/users?size=50', token=admin_token)
check('用户列表(不含管理员)', r['code'] == 0 and all(u['role'] == 'USER' for u in r['data']['list'])
      and all('password' not in u or not u['password'] for u in r['data']['list']), str(r)[:200])
r = call('GET', '/admin/users?keyword=' + urllib.request.quote('apitester'), token=admin_token)
check('按用户名模糊查询', r['data']['total'] >= 1, str(r)[:150])
uid = [u['id'] for u in r['data']['list'] if u['username'] == TEST_USER][0]
r = call('PUT', f'/admin/users/{uid}/status?status=0', token=admin_token)
check('禁用用户成功', r['code'] == 0, str(r))
r = call('POST', '/auth/login', {'username': TEST_USER, 'password': 'test123456'})
check('被禁用用户无法登录', r['code'] == 403 and '禁用' in r['msg'], str(r))
r = call('PUT', f'/admin/users/{uid}/status?status=1', token=admin_token)
check('启用用户成功', r['code'] == 0, str(r))
r = call('POST', '/auth/login', {'username': TEST_USER, 'password': 'test123456'})
check('启用后可重新登录', r['code'] == 0, str(r))

print('==== 十、管理端：数据统计 ====')
r = call('GET', '/admin/stats', token=admin_token)
check('景点总数统计', r['data']['total'] == 75, str(r['data']['total']))
check('各等级分布统计(5A~1A五档)', [i['level'] for i in r['data']['byLevel']] == ['5A', '4A', '3A', '2A', '1A'],
      str(r['data']['byLevel']))
check('各省数量统计(16省)', len(r['data']['byProvince']) == 16, str(len(r['data']['byProvince'])))
sum_by_level = sum(i['count'] for i in r['data']['byLevel'])
check('等级分布合计=总数', sum_by_level == 75, f'sum={sum_by_level}')

print()
print(f'========== 结果：{len(PASSED)} 通过 / {len(FAILED)} 失败 ==========')
if FAILED:
    for name, detail in FAILED:
        print(f'  FAILED: {name}  {detail}')
    sys.exit(1)
print('ALL API TESTS PASSED')

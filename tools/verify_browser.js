// 浏览器端全功能验证脚本（playwright-core + 系统 Edge）
const { chromium } = require('playwright-core');
const fs = require('fs');

const BASE = 'http://localhost';
const SHOTS = 'E:/Spring/tourism-site/tools/shots';
const results = [];
const ok = (name, cond, detail = '') => {
  results.push({ name, pass: !!cond, detail });
  console.log((cond ? '  PASS  ' : '  FAIL  ') + name + (cond ? '' : '  ' + detail));
};

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.setDefaultTimeout(15000);

  // ========== 〇、登录墙：未登录不能看 ==========
  await page.goto(BASE + '/');
  await page.waitForTimeout(800);
  ok('未登录访问首页被守卫拦截到登录页', page.url().includes('/login'), page.url());
  await page.screenshot({ path: SHOTS + '/00-未登录被拦截.png' });

  // 游客登录（预置启用账号）
  await page.goto(BASE + '/login');
  await page.fill('input[placeholder="用户名"]', 'tourist123');
  await page.fill('input[placeholder="登录密码"]', '123456');
  await page.click('button:has-text("登 录")');
  await page.waitForSelector('.card', { timeout: 15000 });

  // ========== 一、前台：列表页 ==========
  let cards = await page.locator('.card').count();
  ok('前台列表页加载出 6 张景点卡片', cards === 6, 'cards=' + cards);
  ok('卡片包含景点名称/等级标签/省市/评分', /故宫博物院/.test(await page.locator('.card').first().textContent())
     && (await page.locator('.card .el-tag').first().textContent()).includes('5A'));
  await page.screenshot({ path: SHOTS + '/01-前台列表页.png', fullPage: true });

  // 关键词搜索
  await page.fill('input[placeholder*="关键词"]', '长城');
  await page.click('button:has-text("搜索")');
  await page.waitForTimeout(800);
  cards = await page.locator('.card').count();
  const searchText = await page.locator('.grid').textContent();
  ok('关键词搜索"长城"有结果且命中八达岭长城', cards >= 1 && searchText.includes('八达岭长城'), 'cards=' + cards);
  await page.screenshot({ path: SHOTS + '/02-关键词搜索.png' });
  await page.click('button:has-text("重置")');
  await page.waitForTimeout(600);

  // 省市联动筛选
  await page.locator('.filter-bar .el-select').nth(0).click(); // 选择省份
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("浙江")').click();
  await page.waitForTimeout(600);
  await page.locator('.filter-bar .el-select').nth(1).click();
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("杭州")').click();
  await page.waitForTimeout(800);
  cards = await page.locator('.card').count();
  const zjText = await page.locator('.grid').textContent();
  ok('省市联动筛选-杭州显示西湖/西溪', cards === 2 && zjText.includes('杭州西湖') && zjText.includes('西溪'), 'cards=' + cards);
  await page.screenshot({ path: SHOTS + '/03-省市筛选.png' });
  await page.click('button:has-text("重置")');
  await page.waitForTimeout(600);

  // 等级筛选
  await page.locator('.filter-bar .el-select').nth(2).click();
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("4A级景区")').click();
  await page.waitForTimeout(800);
  const lvTags = await page.locator('.card .el-tag').allTextContents();
  ok('等级筛选-全部为4A', lvTags.length > 0 && lvTags.every(t => t.trim() === '4A'), JSON.stringify(lvTags));
  await page.screenshot({ path: SHOTS + '/04-等级筛选.png' });
  await page.click('button:has-text("重置")');
  await page.waitForTimeout(600);

  // 排序
  await page.click('text=综合评分排序');
  await page.waitForTimeout(800);
  let ratings = await page.locator('.card .rating').allTextContents();
  const rv1 = ratings.map(s => parseFloat(s.replace('★', '')));
  ok('综合评分排序-降序', rv1.every((v, i) => i === 0 || rv1[i - 1] >= v), JSON.stringify(rv1));
  await page.click('text=浏览热度排序');
  await page.waitForTimeout(800);
  const views = await page.locator('.card .views').allTextContents();
  const vw1 = views.map(s => parseInt(s.replace(/\D/g, '')));
  ok('浏览热度排序-降序', vw1.every((v, i) => i === 0 || vw1[i - 1] >= v), JSON.stringify(vw1));
  await page.screenshot({ path: SHOTS + '/05-热度排序.png' });

  // 分页
  await page.locator('.el-pagination .btn-next').click();
  await page.waitForTimeout(800);
  const pagerText = await page.locator('.el-pagination').textContent();
  ok('分页-第2页可见', pagerText.includes('共 75 条') && await page.locator('.el-pager li.is-active').textContent() === '2', pagerText);
  await page.screenshot({ path: SHOTS + '/06-分页.png' });

  // ========== 二、前台：详情页 ==========
  await page.goto(BASE + '/');
  await page.waitForSelector('.card');
  await page.locator('.card:has-text("故宫博物院")').click();
  await page.waitForSelector('.detail .content');
  const detailText = await page.locator('.detail').textContent();
  ok('详情页-故宫完整信息', detailText.includes('5A级景区') && detailText.includes('景山前街')
     && detailText.includes('开放时间') && detailText.includes('¥60') && detailText.includes('景点介绍'));
  const views1 = (detailText.match(/浏览 (\d+) 次/) || [])[1];
  await page.reload(); await page.waitForTimeout(1000);
  const detailText2 = await page.locator('.detail').textContent();
  const views2 = (detailText2.match(/浏览 (\d+) 次/) || [])[1];
  ok('详情页浏览量刷新后+1', Number(views2) === Number(views1) + 1, `${views1} -> ${views2}`);
  await page.screenshot({ path: SHOTS + '/07-景点详情页.png', fullPage: true });

  // ========== 三、前台：注册 ==========
  await page.goto(BASE + '/register');
  const uname = 'uitest' + Date.now() % 100000;
  await page.fill('input[placeholder*="用户名"]', uname);
  await page.fill('input[placeholder*="昵称"]', '浏览器测试用户');
  await page.fill('input[placeholder*="至少 6 位"]', 'uitest123');
  await page.fill('input[placeholder*="确认密码"]', 'uitest123');
  await page.screenshot({ path: SHOTS + '/08-注册页填写.png' });
  await page.click('button:has-text("注 册")');
  await page.waitForURL('**/login', { timeout: 10000 });
  ok('注册成功跳转登录页', page.url().includes('/login'), page.url());
  await page.screenshot({ path: SHOTS + '/09-注册成功.png' });

  // 注册用户登录
  await page.fill('input[placeholder="用户名"]', uname);
  await page.fill('input[placeholder="登录密码"]', 'uitest123');
  await page.click('button:has-text("登 录")');
  await page.waitForSelector('.card', { timeout: 10000 });
  const navText = await page.locator('.nav-inner').textContent();
  ok('登录成功显示昵称', navText.includes('浏览器测试用户'), navText);
  await page.screenshot({ path: SHOTS + '/10-登录成功.png' });

  // 禁用用户登录被拦（tourist456 预置为禁用）
  await page.click('button:has-text("退出登录")');
  await page.waitForTimeout(600);
  await page.goto(BASE + '/login');
  await page.fill('input[placeholder="用户名"]', 'tourist456');
  await page.fill('input[placeholder="登录密码"]', '123456');
  await page.click('button:has-text("登 录")');
  await page.waitForTimeout(1000);
  const hasErr = await page.locator('.el-message--error').count();
  ok('禁用用户登录弹出错误提示', hasErr > 0);
  await page.screenshot({ path: SHOTS + '/11-禁用用户登录被拦.png' });

  // ========== 四、后台管理端 ==========
  await page.goto(BASE + '/admin/login');
  await page.screenshot({ path: SHOTS + '/12-后台登录页.png' });
  await page.fill('input[placeholder="管理员账号"]', 'admin');
  await page.fill('input[placeholder="登录密码"]', 'admin123');
  await page.click('button:has-text("登 录")');
  await page.waitForSelector('.el-table', { timeout: 10000 });
  ok('后台登录成功进入景点管理', page.url().includes('/admin/attractions'), page.url());

  // 景点管理：新增
  await page.click('button:has-text("新增景点")');
  await page.waitForSelector('.el-dialog');
  await page.fill('.el-dialog input[placeholder], .el-dialog .el-form-item:nth-of-type(1) input', 'UI自动化测试景点');
  // 省市下拉（始终点最新打开的下拉面板，避免 popper 残留误命中）
  const pickFromDialogSelect = async (idx, text) => {
    await page.locator('.el-dialog .el-select').nth(idx).click();
    await page.waitForTimeout(400);
    await page.locator('.el-select-dropdown:visible').last()
      .locator('.el-select-dropdown__item', { hasText: text }).first().click();
    await page.waitForTimeout(300);
  };
  await pickFromDialogSelect(0, '北京'); // 省份
  await pickFromDialogSelect(1, '北京'); // 城市
  await pickFromDialogSelect(2, '3A');   // 等级
  const dlgText1 = await page.locator('.el-dialog').textContent();
  await page.fill('.el-dialog textarea', '由浏览器自动化新增的测试景点，用于验证管理端功能。');
  await page.fill('.el-dialog .el-form-item:nth-of-type(7) input', '自动化测试路1号');
  await page.fill('.el-dialog .el-form-item:nth-of-type(8) input', '10:00-16:00');
  await page.screenshot({ path: SHOTS + '/13-新增景点表单.png' });
  await page.click('.el-dialog button:has-text("保存")');
  await page.waitForTimeout(1000);
  // 条件查询验证新增成功
  await page.fill('.toolbar input', 'UI自动化测试景点');
  await page.click('.toolbar button:has-text("查询")');
  await page.waitForTimeout(800);
  const tblText = await page.locator('.el-table').textContent();
  ok('景点管理-新增后条件查询可见', tblText.includes('UI自动化测试景点') && tblText.includes('3A'), tblText.slice(0, 200));
  await page.screenshot({ path: SHOTS + '/14-新增景点成功.png' });

  // 修改
  await page.click('.el-table button:has-text("修改")');
  await page.waitForSelector('.el-dialog');
  await page.fill('.el-dialog input[placeholder], .el-dialog .el-form-item:nth-of-type(1) input', 'UI自动化测试景点(改)');
  await page.click('.el-dialog button:has-text("保存")');
  await page.waitForTimeout(1000);
  const tblText2 = await page.locator('.el-table').textContent();
  ok('景点管理-修改后列表更新', tblText2.includes('(改)'), tblText2.slice(0, 120));

  // 删除
  page.once('dialog', d => d.accept());
  await page.click('.el-table button:has-text("删除")');
  // Element Plus MessageBox 是 DOM 而非原生 dialog
  await page.waitForSelector('.el-message-box', { timeout: 5000 }).catch(() => {});
  await page.click('.el-message-box button:has-text("确定")').catch(() => {});
  await page.waitForTimeout(1000);
  const tblText3 = await page.locator('.el-table').textContent();
  ok('景点管理-删除后列表无该记录', !tblText3.includes('UI自动化测试景点'), tblText3.slice(0, 120));
  await page.screenshot({ path: SHOTS + '/15-景点删除后.png' });

  // 省市管理
  await page.click('text=🗺️ 省市管理');
  await page.waitForSelector('.el-card');
  const provName = '自动化省' + Date.now() % 100000; // 唯一名，保证脚本可重复运行
  await page.click('button:has-text("新增省份")');
  await page.waitForSelector('.el-dialog');
  await page.fill('.el-dialog input', provName);
  await page.click('.el-dialog button:has-text("保存")');
  await page.waitForSelector('.el-dialog', { state: 'hidden', timeout: 10000 }); // 等弹窗真正关闭
  await page.waitForTimeout(600);
  const provText = await page.locator('.el-col:first-child').textContent();
  ok('省市管理-新增省份出现在列表', provText.includes(provName), provText.slice(0, 300));
  // 删除有景点的省份应被拦截
  page.once('dialog', d => d.accept());
  await page.locator('.el-col:first-child .el-table button:has-text("删除")').first().click();
  await page.waitForSelector('.el-message-box', { timeout: 5000 }).catch(() => {});
  await page.click('.el-message-box button:has-text("确定")').catch(() => {});
  await page.waitForTimeout(1000);
  const stillThere = await page.locator('.el-col:first-child').textContent();
  ok('省市管理-北京仍存在(删除被拦截)', stillThere.includes('北京'), '');
  await page.screenshot({ path: SHOTS + '/16-省市管理.png' });

  // 用户管理
  await page.click('text=👥 用户管理');
  await page.waitForSelector('.el-table');
  await page.waitForSelector('.el-table .el-table__row', { timeout: 10000 }); // 等数据行渲染
  const userTbl = await page.locator('.el-table').textContent();
  ok('用户管理-可见注册用户', userTbl.includes('tourist123') && userTbl.includes(uname), userTbl.slice(0, 200));
  await page.fill('.toolbar input', uname);
  await page.click('.toolbar button:has-text("查询")');
  await page.waitForTimeout(800);
  ok('用户管理-按用户名查询', (await page.locator('.el-table').textContent()).includes(uname));
  // 禁用再启用
  await page.click('.el-table button:has-text("禁用")');
  await page.waitForSelector('.el-message-box');
  await page.click('.el-message-box button:has-text("确定")');
  await page.waitForTimeout(800);
  ok('用户管理-禁用后状态为禁用', (await page.locator('.el-table').textContent()).includes('禁用'));
  await page.screenshot({ path: SHOTS + '/17-用户禁用.png' });
  await page.click('.el-table button:has-text("启用")');
  await page.waitForSelector('.el-message-box');
  await page.click('.el-message-box button:has-text("确定")');
  await page.waitForTimeout(800);
  ok('用户管理-启用后状态为启用', (await page.locator('.el-table').textContent()).includes('启用'));
  await page.screenshot({ path: SHOTS + '/18-用户启用.png' });

  // 数据统计
  await page.click('text=📊 数据统计');
  await page.waitForSelector('canvas', { timeout: 10000 });
  await page.waitForTimeout(1200);
  const statNums = await page.locator('.stat-num').allTextContents();
  ok('数据统计-景点总数=75', statNums[0] === '75', JSON.stringify(statNums));
  const canvasCount = await page.locator('canvas').count();
  ok('数据统计-两张 ECharts 图表渲染', canvasCount >= 2, 'canvas=' + canvasCount);
  await page.screenshot({ path: SHOTS + '/19-数据统计.png', fullPage: true });

  // 退出 + 路由守卫
  await page.click('button:has-text("退出")');
  await page.waitForTimeout(800);
  ok('后台退出后跳回登录页', page.url().includes('/admin/login'), page.url());
  await page.goto(BASE + '/admin/attractions');
  await page.waitForTimeout(800);
  ok('未登录访问后台路由被守卫拦截', page.url().includes('/admin/login'), page.url());

  await browser.close();
  const failed = results.filter(r => !r.pass);
  console.log(`\n========== 浏览器验证：${results.length - failed.length} 通过 / ${failed.length} 失败 ==========`);
  process.exit(failed.length ? 1 : 0);
})().catch(e => { console.error('SCRIPT ERROR:', e.message); process.exit(2); });

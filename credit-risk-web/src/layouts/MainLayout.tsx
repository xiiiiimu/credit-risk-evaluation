import { Layout, Menu, Typography } from 'antd';
import {
  AuditOutlined,
  FileSearchOutlined,
  FormOutlined,
  ProductOutlined,
  ProfileOutlined,
  RadarChartOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Sider, Content } = Layout;

const menuItems = [
  { key: '/apply', icon: <FormOutlined />, label: '信贷申请' },
  { key: '/applications', icon: <UnorderedListOutlined />, label: '我的申请' },
  { key: '/products', icon: <ProductOutlined />, label: '信贷产品' },
  { key: '/admin/reviews', icon: <AuditOutlined />, label: '人工复核' },
  { key: '/admin/trace', icon: <RadarChartOutlined />, label: 'Workflow Trace' },
  { key: '/admin/feedback', icon: <ProfileOutlined />, label: '人工反馈' },
];

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const selectedKey = location.pathname.startsWith('/admin/applications')
    ? '/admin/trace'
    : menuItems.find((m) => location.pathname.startsWith(m.key))?.key || '/apply';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth={64}>
        <div style={{ color: '#fff', padding: 16, fontWeight: 600 }}>信贷风控平台</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => {
            if (key === '/admin/trace') {
              navigate('/admin/applications/0/trace');
            } else {
              navigate(key);
            }
          }}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px' }}>
          <Typography.Title level={4} style={{ margin: '16px 0' }}>
            <FileSearchOutlined /> 智能信贷风控演示
          </Typography.Title>
        </Header>
        <Content style={{ margin: 24, background: '#fff', padding: 24, minHeight: 360 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

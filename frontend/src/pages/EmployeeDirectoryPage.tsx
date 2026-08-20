import { Button, Card, Input, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { get } from '../api/client'
import type { Employee, Page } from '../api/types'

export default function EmployeeDirectoryPage() {
  const navigate = useNavigate()
  const [data, setData] = useState<Page<Employee> | null>(null)
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [country, setCountry] = useState('')
  const [department, setDepartment] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  // guards against out-of-order responses painting stale results
  const requestSeq = useRef(0)

  const load = useCallback(() => {
    const seq = ++requestSeq.current
    setLoading(true)
    get<Page<Employee>>('/employees', {
      page,
      size: 20,
      search: search || undefined,
      country: country || undefined,
      department: department || undefined,
      status,
    })
      .then((result) => {
        if (seq === requestSeq.current) setData(result)
      })
      .catch((e) => message.error(e.message))
      .finally(() => {
        if (seq === requestSeq.current) setLoading(false)
      })
  }, [page, search, country, department, status])

  useEffect(load, [load])

  const columns: ColumnsType<Employee> = [
    {
      title: 'Code',
      dataIndex: 'employeeCode',
      render: (code, row) => <Link to={`/employees/${row.id}`}>{code}</Link>,
    },
    { title: 'Name', dataIndex: 'name' },
    { title: 'Email', dataIndex: 'email' },
    { title: 'Country', dataIndex: 'country' },
    { title: 'Department', dataIndex: 'department' },
    {
      title: 'Annual salary',
      dataIndex: 'annualSalary',
      align: 'right',
      render: (v, row) => `${row.currencyCode} ${Number(v).toLocaleString()}`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s) => <Tag color={s === 'ACTIVE' ? 'green' : 'orange'}>{s}</Tag>,
    },
  ]

  // filters apply on Enter / clear — never per keystroke
  return (
    <Card>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Employees
        </Typography.Title>
        <Space wrap>
          <Input.Search
            placeholder="Search name or code"
            allowClear
            onSearch={(v) => {
              setPage(0)
              setSearch(v)
            }}
            style={{ width: 220 }}
          />
          <Input.Search
            placeholder="Country"
            allowClear
            onSearch={(v) => {
              setPage(0)
              setCountry(v)
            }}
            style={{ width: 150 }}
          />
          <Input.Search
            placeholder="Department"
            allowClear
            onSearch={(v) => {
              setPage(0)
              setDepartment(v)
            }}
            style={{ width: 160 }}
          />
          <Select
            placeholder="Status"
            allowClear
            style={{ width: 120 }}
            options={[
              { value: 'ACTIVE', label: 'Active' },
              { value: 'ON_HOLD', label: 'On hold' },
            ]}
            onChange={(v) => {
              setPage(0)
              setStatus(v)
            }}
          />
          <Button type="primary" onClick={() => navigate('/employees/new')}>
            Add employee
          </Button>
        </Space>
      </Space>
      <Table<Employee>
        rowKey="id"
        columns={columns}
        dataSource={data?.content}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: 20,
          total: data?.totalElements,
          showSizeChanger: false,
          showTotal: (total) => `${total.toLocaleString()} employees`,
        }}
        onChange={(p) => setPage((p.current ?? 1) - 1)}
        size="middle"
      />
    </Card>
  )
}

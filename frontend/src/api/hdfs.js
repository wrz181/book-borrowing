import request from '../utils/request'

/** 执行归档：把全部借阅记录导出 CSV 写入 HDFS（仅管理员） */
export function runArchive() {
  return request.post('/hdfs/archive/run')
}

/** 归档文件列表（仅管理员） */
export function listArchiveFiles() {
  return request.get('/hdfs/archive/files')
}

/** 预览归档文件前 N 行（仅管理员） */
export function previewArchiveFile(fileName, lines = 20) {
  return request.get('/hdfs/archive/preview', { params: { fileName, lines } })
}

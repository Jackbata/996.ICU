#!/usr/bin/env python3
"""
安卓线上问题自动诊断系统
Android Online Issue Auto-Diagnosis System

功能：
1. 解析崩溃日志和异常信息
2. 分析代码调用栈
3. 匹配已知问题模式
4. 生成诊断报告
5. 提供修复建议
"""

import json
import re
import os
import sys
from datetime import datetime
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from pathlib import Path
import argparse

@dataclass
class IssueReport:
    """问题报告数据结构"""
    timestamp: str
    user_id: str
    device_info: str
    app_version: str
    issue_type: str
    severity: str
    stack_trace: str
    log_snippets: List[str]
    diagnosis: str
    suggested_fixes: List[str]
    confidence_score: float

class AndroidLogParser:
    """安卓日志解析器"""
    
    def __init__(self):
        self.crash_patterns = {
            'ANR': r'ANR in.*?pid=\d+',
            'Crash': r'FATAL EXCEPTION.*?Process:',
            'OOM': r'OutOfMemoryError',
            'NullPointer': r'NullPointerException',
            'IllegalState': r'IllegalStateException',
            'Network': r'NetworkOnMainThreadException',
            'Security': r'SecurityException'
        }
        
    def parse_crash_log(self, log_content: str) -> Dict:
        """解析崩溃日志"""
        result = {
            'issue_type': 'Unknown',
            'severity': 'Medium',
            'stack_trace': '',
            'device_info': '',
            'app_version': '',
            'timestamp': ''
        }
        
        # 检测问题类型
        for issue_type, pattern in self.crash_patterns.items():
            if re.search(pattern, log_content, re.IGNORECASE):
                result['issue_type'] = issue_type
                break
                
        # 提取堆栈跟踪
        stack_match = re.search(r'at\s+.*?(?=\n\n|\Z)', log_content, re.DOTALL)
        if stack_match:
            result['stack_trace'] = stack_match.group(0)
            
        # 提取设备信息
        device_match = re.search(r'Build.*?:\s*(.*?)(?:\n|$)', log_content)
        if device_match:
            result['device_info'] = device_match.group(1)
            
        # 提取应用版本
        version_match = re.search(r'versionName.*?:\s*(.*?)(?:\n|$)', log_content)
        if version_match:
            result['app_version'] = version_match.group(1)
            
        return result

class CodeAnalyzer:
    """代码分析器"""
    
    def __init__(self, project_path: str):
        self.project_path = Path(project_path)
        self.java_files = []
        self.kotlin_files = []
        self._scan_source_files()
        
    def _scan_source_files(self):
        """扫描源码文件"""
        for file_path in self.project_path.rglob("*.java"):
            self.java_files.append(file_path)
        for file_path in self.project_path.rglob("*.kt"):
            self.kotlin_files.append(file_path)
            
    def find_method_by_stack_trace(self, stack_trace: str) -> List[Dict]:
        """根据堆栈跟踪查找对应的方法"""
        methods = []
        lines = stack_trace.split('\n')
        
        for line in lines:
            if 'at ' in line:
                # 解析堆栈行: at com.example.MyClass.method(MyClass.java:123)
                match = re.search(r'at\s+([^(]+)\.([^(]+)\(([^:]+):(\d+)\)', line)
                if match:
                    package_class = match.group(1)
                    method_name = match.group(2)
                    file_name = match.group(3)
                    line_number = int(match.group(4))
                    
                    # 查找对应的源码文件
                    source_file = self._find_source_file(file_name)
                    if source_file:
                        method_info = self._extract_method_context(source_file, line_number)
                        if method_info:
                            methods.append({
                                'package_class': package_class,
                                'method_name': method_name,
                                'file_path': str(source_file),
                                'line_number': line_number,
                                'context': method_info
                            })
        return methods
        
    def _find_source_file(self, file_name: str) -> Optional[Path]:
        """查找源码文件"""
        all_files = self.java_files + self.kotlin_files
        for file_path in all_files:
            if file_path.name == file_name:
                return file_path
        return None
        
    def _extract_method_context(self, file_path: Path, line_number: int, context_lines: int = 10) -> Optional[str]:
        """提取方法上下文"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                lines = f.readlines()
                
            start = max(0, line_number - context_lines - 1)
            end = min(len(lines), line_number + context_lines)
            
            context = ''.join(lines[start:end])
            return context
        except Exception as e:
            print(f"Error reading file {file_path}: {e}")
            return None

class IssueDiagnoser:
    """问题诊断器"""
    
    def __init__(self, project_path: str):
        self.log_parser = AndroidLogParser()
        self.code_analyzer = CodeAnalyzer(project_path)
        self.knowledge_base = self._load_knowledge_base()
        
    def _load_knowledge_base(self) -> Dict:
        """加载问题知识库"""
        return {
            'NullPointerException': {
                'common_causes': [
                    '对象未初始化就调用方法',
                    '从集合中获取元素时未检查null',
                    '网络请求返回null未处理',
                    'SharedPreferences获取值为null'
                ],
                'fixes': [
                    '添加null检查: if (object != null)',
                    '使用Optional或?.操作符',
                    '提供默认值: object ?: defaultValue',
                    '使用Objects.requireNonNull()'
                ]
            },
            'OutOfMemoryError': {
                'common_causes': [
                    '图片未压缩直接加载',
                    '内存泄漏导致对象无法回收',
                    '大量数据同时加载到内存',
                    '静态变量持有大对象引用'
                ],
                'fixs': [
                    '使用图片压缩库如Glide',
                    '检查内存泄漏，使用LeakCanary',
                    '分页加载大数据集',
                    '及时释放不需要的对象'
                ]
            },
            'ANR': {
                'common_causes': [
                    '主线程执行耗时操作',
                    '数据库操作在主线程',
                    '网络请求在主线程',
                    '复杂计算在主线程'
                ],
                'fixes': [
                    '使用AsyncTask或线程池',
                    '数据库操作移到后台线程',
                    '使用Retrofit等异步网络库',
                    '使用Handler.post()延迟执行'
                ]
            }
        }
        
    def diagnose_issue(self, log_content: str, user_id: str = "unknown") -> IssueReport:
        """诊断问题"""
        # 解析日志
        parsed_log = self.log_parser.parse_crash_log(log_content)
        
        # 分析代码
        methods = self.code_analyzer.find_method_by_stack_trace(parsed_log['stack_trace'])
        
        # 生成诊断
        diagnosis, fixes, confidence = self._generate_diagnosis(
            parsed_log['issue_type'], 
            parsed_log['stack_trace'],
            methods
        )
        
        # 创建报告
        report = IssueReport(
            timestamp=datetime.now().isoformat(),
            user_id=user_id,
            device_info=parsed_log['device_info'],
            app_version=parsed_log['app_version'],
            issue_type=parsed_log['issue_type'],
            severity=parsed_log['severity'],
            stack_trace=parsed_log['stack_trace'],
            log_snippets=[log_content[:500]],  # 截取前500字符
            diagnosis=diagnosis,
            suggested_fixes=fixes,
            confidence_score=confidence
        )
        
        return report
        
    def _generate_diagnosis(self, issue_type: str, stack_trace: str, methods: List[Dict]) -> Tuple[str, List[str], float]:
        """生成诊断结果"""
        if issue_type in self.knowledge_base:
            kb = self.knowledge_base[issue_type]
            diagnosis = f"检测到{issue_type}异常。"
            
            # 分析可能的原因
            causes = []
            for cause in kb['common_causes']:
                if any(keyword in stack_trace.lower() for keyword in ['null', 'memory', 'thread']):
                    causes.append(cause)
                    
            if causes:
                diagnosis += f" 可能原因：{', '.join(causes[:2])}"
                
            fixes = kb['fixes'][:3]  # 取前3个修复建议
            confidence = 0.8 if causes else 0.5
            
        else:
            diagnosis = f"检测到{issue_type}异常，需要进一步分析。"
            fixes = [
                "检查相关代码逻辑",
                "查看完整日志信息",
                "联系开发团队"
            ]
            confidence = 0.3
            
        return diagnosis, fixes, confidence

class ReportGenerator:
    """报告生成器"""
    
    @staticmethod
    def generate_html_report(report: IssueReport) -> str:
        """生成HTML格式的诊断报告"""
        html = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <title>安卓问题诊断报告</title>
            <meta charset="utf-8">
            <style>
                body {{ font-family: Arial, sans-serif; margin: 20px; }}
                .header {{ background-color: #f0f0f0; padding: 15px; border-radius: 5px; }}
                .section {{ margin: 20px 0; }}
                .code {{ background-color: #f5f5f5; padding: 10px; border-radius: 3px; font-family: monospace; }}
                .fix {{ background-color: #e8f5e8; padding: 10px; margin: 5px 0; border-left: 3px solid #4caf50; }}
                .confidence {{ color: {'#4caf50' if report.confidence_score > 0.7 else '#ff9800' if report.confidence_score > 0.4 else '#f44336'}; }}
            </style>
        </head>
        <body>
            <div class="header">
                <h1>🔍 安卓问题诊断报告</h1>
                <p><strong>时间:</strong> {report.timestamp}</p>
                <p><strong>用户ID:</strong> {report.user_id}</p>
                <p><strong>应用版本:</strong> {report.app_version}</p>
                <p><strong>设备信息:</strong> {report.device_info}</p>
            </div>
            
            <div class="section">
                <h2>📊 问题概览</h2>
                <p><strong>问题类型:</strong> {report.issue_type}</p>
                <p><strong>严重程度:</strong> {report.severity}</p>
                <p><strong>诊断置信度:</strong> <span class="confidence">{report.confidence_score:.1%}</span></p>
            </div>
            
            <div class="section">
                <h2>🔬 诊断结果</h2>
                <p>{report.diagnosis}</p>
            </div>
            
            <div class="section">
                <h2>🛠️ 修复建议</h2>
                {''.join([f'<div class="fix">{fix}</div>' for fix in report.suggested_fixes])}
            </div>
            
            <div class="section">
                <h2>📋 堆栈跟踪</h2>
                <div class="code">{report.stack_trace}</div>
            </div>
            
            <div class="section">
                <h2>📝 相关日志</h2>
                <div class="code">{report.log_snippets[0] if report.log_snippets else '无'}</div>
            </div>
        </body>
        </html>
        """
        return html
        
    @staticmethod
    def generate_json_report(report: IssueReport) -> str:
        """生成JSON格式的诊断报告"""
        return json.dumps({
            'timestamp': report.timestamp,
            'user_id': report.user_id,
            'device_info': report.device_info,
            'app_version': report.app_version,
            'issue_type': report.issue_type,
            'severity': report.severity,
            'diagnosis': report.diagnosis,
            'suggested_fixes': report.suggested_fixes,
            'confidence_score': report.confidence_score,
            'stack_trace': report.stack_trace
        }, indent=2, ensure_ascii=False)

def main():
    parser = argparse.ArgumentParser(description='安卓线上问题自动诊断系统')
    parser.add_argument('--log-file', required=True, help='崩溃日志文件路径')
    parser.add_argument('--project-path', required=True, help='安卓项目源码路径')
    parser.add_argument('--user-id', default='unknown', help='用户ID')
    parser.add_argument('--output-format', choices=['html', 'json'], default='html', help='输出格式')
    parser.add_argument('--output-file', help='输出文件路径')
    
    args = parser.parse_args()
    
    # 读取日志文件
    try:
        with open(args.log_file, 'r', encoding='utf-8') as f:
            log_content = f.read()
    except FileNotFoundError:
        print(f"错误: 找不到日志文件 {args.log_file}")
        sys.exit(1)
        
    # 创建诊断器
    diagnoser = IssueDiagnoser(args.project_path)
    
    # 执行诊断
    print("🔍 开始诊断问题...")
    report = diagnoser.diagnose_issue(log_content, args.user_id)
    
    # 生成报告
    if args.output_format == 'html':
        report_content = ReportGenerator.generate_html_report(report)
        output_file = args.output_file or f"diagnosis_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.html"
    else:
        report_content = ReportGenerator.generate_json_report(report)
        output_file = args.output_file or f"diagnosis_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    
    # 保存报告
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(report_content)
        
    print(f"✅ 诊断完成！报告已保存到: {output_file}")
    print(f"📊 问题类型: {report.issue_type}")
    print(f"🎯 置信度: {report.confidence_score:.1%}")
    print(f"💡 修复建议数量: {len(report.suggested_fixes)}")

if __name__ == "__main__":
    main()
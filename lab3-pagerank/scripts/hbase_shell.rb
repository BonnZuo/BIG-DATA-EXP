student = 'lab3_shell_Student'
course = 'lab3_shell_Course'
sc = 'lab3_shell_SC'
tables = list
[student, course, sc].each do |name|
  if tables.include?(name)
    disable name if is_enabled(name)
    drop name
  end
end
create student, 'Info', 'Score'
create course, 'Info'
create sc, 'Info'
File.foreach('data/student.tsv') do |line|
  name, id, sex, age = line.chomp.split("\t")
  put student, name, 'Info:S_No', id
  put student, name, 'Info:S_Sex', sex
  put student, name, 'Info:S_Age', age
end
File.foreach('data/course.tsv') do |line|
  id, name, credit = line.chomp.split("\t")
  put course, id, 'Info:C_Name', name
  put course, id, 'Info:C_Credit', credit
end
File.foreach('data/sc.tsv') do |line|
  sno, cno, score = line.chomp.split("\t")
  row = "#{sno}##{cno}"
  put sc, row, 'Info:SC_Sno', sno
  put sc, row, 'Info:SC_Cno', cno
  put sc, row, 'Info:SC_Score', score
end
puts '=== LIST / SCAN / COUNT ==='
list
[student, course, sc].each do |name|
  scan name
  count name
end
puts '=== ADD FAMILY AND QUALIFIER ==='
alter student, {NAME => 'Extra'}
put student, 'Mary', 'Extra:note', 'lab'
get student, 'Mary', 'Extra:note'
puts '=== MODIFY AND SCAN ==='
put student, 'Zhangsan', 'Score:Math', '86'
put student, 'Zhangsan', 'Score:English', '69'
put student, 'Zhangsan', 'Score:Math', '90'
scan student, {COLUMNS => 'Score'}
scan student, {COLUMNS => 'Score:Math'}
puts '=== DELETE QUALIFIER AND FAMILY ==='
deleteall student, 'Zhangsan', 'Score:English'
get student, 'Zhangsan', 'Score:English'
alter student, {NAME => 'Extra', METHOD => 'delete'}
describe student
puts '=== DELETE ROW / COUNT ==='
deleteall student, 'Lisi'
count student
puts '=== TRUNCATE / COUNT ==='
truncate student
count student
exit

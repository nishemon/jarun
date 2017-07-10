
function convert(jsonStr, clazz) {
	var targetClass = Java.type(clazz);
	var bean = new targetClass();
	var obj = JSON.parse(jsonStr);
	var k, v;
	for (k in obj) {
		v = obj[k];
		bean[k] = v;
	}
	return bean;
}

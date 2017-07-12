
function createAndCopy(obj, clazz) {
	var targetClass = Java.type(clazz);
	var bean = new targetClass();
	var k, v;
	for (k in obj) {
		v = obj[k];
		if (typeof v !== 'object') {
			bean[k] = v;
		}
	}
	return bean;
}

function convert(jsonStr, clazz) {
	return createAndCopy(JSON.parse(jsonStr), clazz);
}

function convertArray(jsonObjStr, key, clazz) {
	var targetClass = Java.type(clazz);
	var ArrayList = Java.type('java.util.ArrayList');
	var arr = JSON.parse(jsonObjStr)[key];
	var i;
	list = new ArrayList();
	for (i = 0; i < arr.length; i++) {
		list.add(createAndCopy(arr[i], clazz));
	}
	return list;
}

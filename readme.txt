�������framework�и��µ���AS�޷�����С���㲥���ɰ�������ķ�������9.0����������һ���µ�framework.jar����������framework�йص�jar������telephony-common.jar
       ��build/core/dex_preopt.mk�У��ҵ�������䣺
     		# Conditional to building on linux, as dex2oat currently does not work on darwin.
				ifeq ($(HOST_OS),linux)
				  WITH_DEXPREOPT ?= true
				  
			��WITH_DEXPREOPT ?= true�滻���������䣬��ȫ��
		
			  ifeq (userdebug,$(TARGET_BUILD_VARIANT))
			    WITH_DEXPREOPT ?= false
			  else
			    WITH_DEXPREOPT ?= true
			  endif
			  
			  ������ɺ���out\target\common\obj\JAVA_LIBRARIES\framework_intermediatesĿ¼���ҵ�classes.jar�����临�Ƶ���ǰĿ¼�����libsĿ¼����
			  ������AS���룬���ɱ���OK��
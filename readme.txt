如果由于framework有更新导致AS无法编译小区广播，可按照下面的方法，在9.0工程中生成一份新的framework.jar或者其它和framework有关的jar包，如telephony-common.jar
       在build/core/dex_preopt.mk中，找到如下语句：
     		# Conditional to building on linux, as dex2oat currently does not work on darwin.
				ifeq ($(HOST_OS),linux)
				  WITH_DEXPREOPT ?= true
				  
			将WITH_DEXPREOPT ?= true替换成下面的语句，再全编
		
			  ifeq (userdebug,$(TARGET_BUILD_VARIANT))
			    WITH_DEXPREOPT ?= false
			  else
			    WITH_DEXPREOPT ?= true
			  endif
			  
			  编译完成后，在out\target\common\obj\JAVA_LIBRARIES\framework_intermediates目录下找到classes.jar，将其复制到当前目录下面的libs目录里面
			  重新用AS编译，即可编译OK。
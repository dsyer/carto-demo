
srcs := target/generated-sources/openapi

build := target/build
includes := $(build)/release/include/k8s
libs := $(build)/release/lib
lib := $(libs)/libk8s.a

release.tgz := k8s-wasm.tgz

RELEASE: $(release.tgz)

ALL: $(lib) $(libs)

$(release.tgz): ALL
	mkdir -p $(includes)
	cp $(srcs)/include/*.h $(srcs)/model/*.h $(includes)
	(cd $(build)/release && tar -czvf - *) > $(release.tgz)

src := $(wildcard $(srcs)/model/*.c)
OBJS := $(src:.c=.o)

$(lib):  $(OBJS) $(libs)
	$(AR) $(ARFLAGS) $@ $(OBJS)

$(libs):
	mkdir -p $(libs)

clean:
	rm -rf target/build $(srcs)/model/*.o
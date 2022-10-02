package androidx.databinding;

public class DataBinderMapperImpl extends MergedDataBinderMapper {
  DataBinderMapperImpl() {
    addMapper(new org.pytorch.demo.handseye.DataBinderMapperImpl());
  }
}

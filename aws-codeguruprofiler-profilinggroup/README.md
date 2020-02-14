# AWS::CodeGuruProfiler::ProfilingGroup

Congratulations on starting development! Next steps:

1. Write the JSON schema describing your resource, `aws-codeguruprofiler-profilinggroup.json`.
2. The RPDK will automatically generate the correct resource model from the
   schema whenever the project is built via Maven. You can also do this manually
   with the following command: `cfn generate`.
3. Implement/Modify your resource handlers.

Please don't modify files under `target/generated-sources/rpdk`, as they will be
automatically overwritten.

After modifying the JSON schema or changing the handlers implementation make sure you do the following before sending a pull-request:

```
pre-commit run --all-files && mvn clean package
```

**References**

- https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-walkthrough.html
- https://docs.amazonaws.cn/en_us/cloudformation-cli/latest/userguide/resource-type-schema.html
- https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-kms/blob/master/alias
- https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-athena/tree/master/namedquery
- https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-s3/tree/master/aws-s3-bucket
- The code use [Lombok](https://projectlombok.org/), and [you may have to install IDE integrations](https://projectlombok.org/) to enable auto-complete for Lombok-annotated classes.


## How do I test this in my account?

1. Get credentials with ADA (if you are Amazonian) or with any other way.
2. Run the following to register the resource type to your account (only needed after changes) and create the required CloudFormation stacks:
    ```
    cfn submit -v --region us-east-1
    ```
2. Create a sample CloudFormation stack that defines a profiling group:
    ```
    aws cloudformation create-stack --region us-east-1 --template-body "file://sample-template.json" --stack-name "sample-profiling-group-resource-creation"
    ```
3. Validate the creation of the profiling group!
4. Delete the sample stack:
    ```
    aws cloudformation delete-stack --region us-east-1 --stack-name "sample-profiling-group-resource-creation"
    ```
5. Validate the profiling group has been deleted!

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
pre-commit run --all-files && AWS_REGION=us-east-1 mvn clean verify package
```

**References**

- https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-walkthrough.html
- https://docs.amazonaws.cn/en_us/cloudformation-cli/latest/userguide/resource-type-schema.html
- https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-kms/blob/master/alias
- https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-athena/tree/master/namedquery
- https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-s3/tree/master/aws-s3-bucket
- The code use [Lombok](https://projectlombok.org/), and [you may have to install IDE integrations](https://projectlombok.org/) to enable auto-complete for Lombok-annotated classes.


## How do I test this in my account?

#### Through CFN

1. Setup your AWS credentials locally.

2. Initialize the project:
   ```
   cfn init
   ```

3. Run the following to register the resource type to your account (only needed after changes) and create the required CloudFormation stacks:
    ```
    cfn submit -v --region us-east-1
    ```
4. Update the default version used by CloudFormation:
    ```
    # Get the latest version in your account
    Arn=`aws cloudformation list-type-versions --region us-east-1 --type RESOURCE --type-name "AWS::CodeGuruProfiler::ProfilingGroup" | jq '.TypeVersionSummaries[].Arn' | sort -nr | head -n 1 | tr -d '"'`

    # Update the default version used by CloudFormation
    aws cloudformation set-type-default-version --region us-east-1 --arn "$Arn"
    ```

5. Create a sample CloudFormation stack that defines a profiling group:
    ```
    aws cloudformation create-stack --region us-east-1 --template-body "file://sample-template.json" --stack-name "sample-profiling-group-resource-creation" --capabilities CAPABILITY_IAM
    ```

6. Validate the creation of the profiling group!

7. Delete the sample stack:
    ```
    aws cloudformation delete-stack --region us-east-1 --stack-name "sample-profiling-group-resource-creation"
    ```

8. Validate the profiling group has been deleted!

#### Through SAM

1. Follow instruction stated on [here](https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-walkthrough.html#resource-type-walkthrough-test).

#### Dealing with CI Failures
If outside of Amazon, you will not be able to see the CI outputs. Our reviewers will be responsible for making that
information available to you. Reviewers, you can find info on how to get CI outputs at [link](https://tinyurl.com/yagdpv7q).

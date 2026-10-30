"""Opt-in internal registration only; no HTTP routes, environment reads or cloud-client creation."""

import logging

from video_serverless.adapters.mongo_output_registration import MongoOutputRegistration
from video_serverless.adapters.oss_output_registration import OssOutputRegistration
from video_serverless.business.local_output_registration import LocalOutputRegistration


def build_local_output_registration(database_provider, bucket, http_get, ids, clock):
    warning = logging.getLogger(__name__).warning
    return LocalOutputRegistration(MongoOutputRegistration(database_provider, clock),
                                   OssOutputRegistration(bucket, http_get, warning), ids, clock, warning)

from datetime import datetime
from django.db import models
from django.utils.translation import gettext_lazy as _

class MotdMessageManager(models.Manager):
    def active_messages(self):
        dtnow = datetime.utcnow()
        return super(MotdMessageManager, self).get_query_set().filter(
            enabled=True, start_time__lte=dtnow, end_time__gte=dtnow)

class MotdMessage(models.Model):
    enabled = models.BooleanField(default=True)
    start_time = models.DateTimeField()
    end_time = models.DateTimeField()
    name = models.CharField(_('name'), unique=True, max_length=100)
    content = models.TextField(_('content'), blank=True)
    
    objects = MotdMessageManager()
    
    class Meta:
        ordering = ['start_time']
    
    def __unicode__(self):
        return self.name

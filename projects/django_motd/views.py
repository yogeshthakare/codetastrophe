from django.contrib.auth import REDIRECT_FIELD_NAME
from django.conf import settings
from django.http import HttpResponseRedirect
from django.shortcuts import render_to_response
from django.template import RequestContext
from models import MotdMessage

def motd(request, template_name='motd/messages.html', redirect_field_name=REDIRECT_FIELD_NAME):
    redirect_to = request.REQUEST.get(redirect_field_name, '')
    if not redirect_to or '//' in redirect_to or ' ' in redirect_to:
        redirect_to = settings.LOGIN_REDIRECT_URL
    messages = MotdMessage.objects.active_messages()
    if not messages:
        return HttpResponseRedirect(redirect_to)
    return render_to_response(template_name, {redirect_field_name: redirect_to,
                'messages': messages}, context_instance=RequestContext(request))

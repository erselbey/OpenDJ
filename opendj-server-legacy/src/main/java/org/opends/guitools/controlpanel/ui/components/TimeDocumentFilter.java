/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.components;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/** Document filter used to update properly a text component displaying a time. */
public class TimeDocumentFilter extends DocumentFilter
{
  private JTextComponent tf;

  /**
   * Constructor.
   * @param tf the text component associated with the document.
   */
  public TimeDocumentFilter(JTextComponent tf)
  {
    this.tf = tf;
  }

  @Override
  public void insertString(DocumentFilter.FilterBypass fb, int offset,
      String text, AttributeSet attr)
  throws BadLocationException
  {
    int previousLength = fb.getDocument().getLength();
    fb.insertString(offset, text.replaceAll("[^0-9]", ""), attr);
    trimPosition(fb, text, offset, previousLength);
  }

  @Override
  public void remove(DocumentFilter.FilterBypass fb, int offset,
      int length)
  throws BadLocationException
  {
    String text = fb.getDocument().getText(offset, length);
    int index = text.indexOf(":");
    if (index == -1)
    {
      fb.remove(offset, length);
    }
    else
    {
      // index value is relative to offset
      if (index > 0)
      {
        fb.remove(offset, index);
      }
      if (index < length - 1)
      {
        fb.remove(offset + index + 1, length - index -1);
      }
    }
    updateCaretPosition(fb);
  }

  @Override
  public void replace(DocumentFilter.FilterBypass fb, int offset,
      int length, String text, AttributeSet attr)
  throws BadLocationException
  {
    int previousLength = fb.getDocument().getLength();

    String t = fb.getDocument().getText(offset, length);
    int index = t.indexOf(":");
    fb.replace(offset, length, text.replaceAll("[^0-9]", ""), attr);
    if (index != -1)
    {
      if (fb.getDocument().getLength() >= 2)
      {
        fb.insertString(2, ":", attr);
      }
      else
      {
        fb.insertString(fb.getDocument().getLength(), ":", attr);
      }
    }

    trimPosition(fb, text, offset, previousLength);
  }

  private void trimPosition(DocumentFilter.FilterBypass fb, String newText,
      int offset, int previousLength)
  throws BadLocationException
  {
    String allText =
      fb.getDocument().getText(0, fb.getDocument().getLength());
    int index = allText.indexOf(':');
    if (index != -1 && newText.length() == 1)
    {
      int minuteLength = allText.length() - index - 1;
      int hourLength = index;

      if (minuteLength > 2 || hourLength > 2)
      {
        if (offset < previousLength)
        {
          fb.remove(offset + 1, 1);
        }
        else
        {
          fb.remove(previousLength, 1);
        }
      }
    }
    updateCaretPosition(fb);
  }

  private void updateCaretPosition(DocumentFilter.FilterBypass fb)
  throws BadLocationException
  {
    String allText =
      fb.getDocument().getText(0, fb.getDocument().getLength());
    int index = allText.indexOf(':');
    if (index != -1)
    {
      int minuteLength = allText.length() - index - 1;
      int hourLength = index;
      int caretPosition = tf.getCaretPosition();

      if (minuteLength >= 2 &&
          caretPosition == allText.length())
      {
        tf.setCaretPosition(0);
      }
      else if (hourLength == caretPosition)
      {
        if (hourLength >= 2)
        {
          tf.setCaretPosition(3);
        }
        else if (hourLength == 1)
        {
          char c = allText.charAt(0);
          if (c != '0' && c != '1' && c != '2')
          {
            tf.setCaretPosition(2);
          }
        }
      }
      else if (hourLength + 1 == caretPosition)
      {
        if (hourLength == 1)
        {
          char c = allText.charAt(0);
          if (c == '0' || c == '1' || c == '2')
          {
            tf.setCaretPosition(caretPosition - 1);
          }
        }
        else if (hourLength == 0)
        {
          tf.setCaretPosition(caretPosition - 1);
        }
      }
    }
    if (allText.length() == 1)
    {
      tf.setCaretPosition(0);
    }
  }
}
